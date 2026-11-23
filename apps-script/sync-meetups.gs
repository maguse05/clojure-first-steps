/**
 * Bound to the "Clojure Meetup Pilot Program Application (Responses)"
 * spreadsheet. It does two things for the website:
 *
 * syncMeetups runs every hour and publishes the approved meetups (Yes under
 * "Sponsorship Setup?") to resources/meetups-sheet.json in the website's
 * repository. Vercel redeploys the site on every commit to main, and nothing is
 * committed when nothing changed. Only public fields leave the spreadsheet:
 * no emails, scores, grants or review notes. Post-Event Reports are matched to
 * a meetup by organizer, on email when the report has one, otherwise on name.
 * The latest one gives the next meetup date, and each one a past meetup with
 * its title, recording and blog post.
 *
 * Photos come from the past meetups folder in Drive: one folder per meetup,
 * named like the meetup, with a folder per date ("Nov 24, 2026" or
 * "2026-11-24") holding the photos. A Google Doc in a date's folder adds its
 * links, e.g. "Slides: https://...". Photos are resized and committed under
 * resources/public/photos, so the folder doesn't need to be public.
 *
 * doPost takes the applications sent from the website's form and adds them to
 * the responses sheet, the way the Google Form did before it was closed, then
 * emails the team.
 *
 * Setup:
 *   1. Project Settings > Script Properties: add GITHUB_TOKEN, a fine-grained
 *      token with Contents read and write access to REPOSITORY only.
 *   2. Run setUp once and accept the permissions. It logs the secret the
 *      website needs as APPLICATIONS_SECRET.
 *   3. Deploy > New deployment > Web app, executed as you, accessible by
 *      anyone. Its URL is the website's APPLICATIONS_URL.
 *
 * After changing this file, run setUp again in case it needs new permissions,
 * and publish a new version of the web app: Deploy > Manage deployments >
 * Edit > Version: New version. The URL stays the same.
 */

const REPOSITORY = 'maguse05/clojure-first-steps';
const BRANCH = 'main';
const FILE_PATH = 'resources/meetups-sheet.json';
const PHOTOS_PATH = 'resources/public/photos';

const APPLICATIONS_SHEET = 'Form Responses 1';
const REPORTS_SPREADSHEET_ID = '11FZuqHJ0i4lz6cQtjKDT09uSXU7-oRyN-C6c73Srzjc';
const REPORTS_SHEET = 'Form Responses 1';

const PAST_MEETUPS_FOLDER_ID = '1fgxL4KNZmlxOHM9vCS8uYDm_9snJjQWw';
const PHOTO_SIZE = 1600;
const NEW_PHOTOS_PER_RUN = 30;

const TEAM_EMAIL = 'clojure-meetups@nubank.com.br';

/**
 * Columns are found by header, or by how the header starts, so the
 * parentheses after "Where do you plan to hold the meetup?" can change.
 */
const APPLICATION_COLUMNS = {
  timestamp: 'Timestamp',
  email: 'Email address',
  organizer: 'Full Name',
  name: 'Final Meetup name',
  format: 'Meetup format',
  country: 'Country',
  city: 'Where do you plan to hold the meetup?',
  frequency: 'How frequently do you plan to hold the meetup?',
  otherFrequency: "If 'Other' in the previous question, please specify your planned frequency.",
  approved: 'Sponsorship Setup?',
  links: 'Generic Meetup Link',
  proposedName: 'Name of the Clojure Meetup?',
};

const REPORT_COLUMNS = {
  timestamp: 'Timestamp',
  email: 'Email Address',
  organizer: "Meetup Organizer's Full Name",
  nextMeetup: 'When do you expect the next meetup to be held?',
  date: 'Date of the Meetup',
  title: 'Meetup Name',
  blog: 'If a blog post was written about the Meetup',
  recording: 'If the Meetup was recorded',
};

function setUp() {
  const properties = PropertiesService.getScriptProperties();
  if (!properties.getProperty('WEBSITE_SECRET')) {
    properties.setProperty('WEBSITE_SECRET', Utilities.getUuid());
  }
  console.log(`APPLICATIONS_SECRET for the website: ${properties.getProperty('WEBSITE_SECRET')}`);

  ScriptApp.getProjectTriggers().forEach(trigger => ScriptApp.deleteTrigger(trigger));
  ScriptApp.newTrigger('syncMeetups').timeBased().everyHours(1).create();
  syncMeetups();
}

function syncMeetups() {
  const spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
  const applications = spreadsheet.getSheetByName(APPLICATIONS_SHEET);
  const reportsSpreadsheet = SpreadsheetApp.openById(REPORTS_SPREADSHEET_ID);
  const reports = readRows(reportsSpreadsheet.getSheetByName(REPORTS_SHEET), REPORT_COLUMNS, ['blog', 'recording']);
  const zones = {
    applications: spreadsheet.getSpreadsheetTimeZone(),
    reports: reportsSpreadsheet.getSpreadsheetTimeZone(),
  };

  const repository = readRepository();
  const photos = photoUploader(repository);
  const folders = meetupFolders();

  const meetups = readRows(applications, APPLICATION_COLUMNS, ['links'])
    .filter(row => isYes(row.approved))
    .map(row => {
      const own = reports.filter(report => sameOrganizer(report, row));
      const folder = folders.take(row.name) || folders.take(row.proposedName);
      return toMeetup(row, own, pastMeetups(own, folder, zones, photos), zones);
    });

  folders.unused().forEach(name => console.warn(`No approved meetup is named like the folder "${name}"`));
  if (photos.postponed()) console.log(`${photos.postponed()} photos are left for the next run`);

  const content = JSON.stringify({ meetups }, null, 2) + '\n';
  const changes = [
    ...(gitBlobSha(content) === repository.files.get(FILE_PATH)
      ? []
      : [{ path: FILE_PATH, mode: '100644', type: 'blob', content }]),
    ...photos.changes(),
  ];
  if (changes.length) {
    commit(repository, changes, 'Sync meetups from the applications sheet');
    console.log(`Published ${meetups.length} meetups`);
  }
}

/**
 * The website posts {secret, answers}, answers keyed by column header. Cells
 * that would start a formula are stored as text.
 */
function doPost(e) {
  const { secret, answers } = JSON.parse(e.postData.contents);
  if (!secret || secret !== PropertiesService.getScriptProperties().getProperty('WEBSITE_SECRET')) {
    return respond({ ok: false, error: 'Wrong secret' });
  }

  const sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(APPLICATIONS_SHEET);
  const headers = sheet.getRange(1, 1, 1, sheet.getLastColumn()).getValues()[0];
  const row = headers.map(() => '');
  row[columnOf(headers, APPLICATION_COLUMNS.timestamp)] = new Date();
  Object.entries(answers).forEach(([question, answer]) => {
    const column = columnOf(headers, question);
    if (column < 0) console.warn(`No column for "${question}", the answer is only in the email`);
    else row[column] = asText(answer);
  });

  const lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    sheet.appendRow(row);
  } finally {
    lock.releaseLock();
  }

  notifyTeam(answers);
  return respond({ ok: true });
}

function notifyTeam(answers) {
  MailApp.sendEmail({
    to: TEAM_EMAIL,
    name: 'Clojure Meetups website',
    replyTo: answers[APPLICATION_COLUMNS.email] || '',
    subject: 'New Clojure meetup application',
    body: Object.entries(answers).map(([question, answer]) => `${question}\n${answer}`).join('\n\n') +
      `\n\n${SpreadsheetApp.getActiveSpreadsheet().getUrl()}`,
  });
}

function respond(body) {
  return ContentService.createTextOutput(JSON.stringify(body))
    .setMimeType(ContentService.MimeType.JSON);
}

function asText(value) {
  const text = String(value == null ? '' : value).trim();
  return /^[=+\-@]/.test(text) ? `'${text}` : text;
}

/**
 * Rows as objects keyed like `columns`, found by header so that moving or
 * adding columns doesn't break anything. For the `linkColumns`, the value is
 * every URL in the cell: typed, from a HYPERLINK formula or a rich text link.
 */
function readRows(sheet, columns, linkColumns = []) {
  const range = sheet.getDataRange();
  const [headers, ...values] = range.getValues();
  const formulas = range.getFormulas().slice(1);
  const richText = range.getRichTextValues().slice(1);
  return values
    .map((cells, i) => Object.fromEntries(Object.entries(columns).map(([key, header]) => {
      const column = columnOf(headers, header);
      if (column < 0) return [key, linkColumns.includes(key) ? [] : ''];
      if (!linkColumns.includes(key)) return [key, cells[column]];

      const text = richText[i][column];
      const linked = text ? text.getRuns().map(run => run.getLinkUrl()) : [];
      return [key, unique([cells[column], formulas[i][column], ...linked].flatMap(urlsIn))];
    })))
    .filter(row => row.timestamp instanceof Date);
}

function columnOf(headers, name) {
  const trimmed = headers.map(header => clean(header));
  const exact = trimmed.indexOf(clean(name));
  return exact >= 0 ? exact : trimmed.findIndex(header => header.startsWith(clean(name)));
}

function toMeetup(row, reports, pastMeetups, zones) {
  const frequency = clean(row.frequency).startsWith('Other') ? row.otherFrequency : row.frequency;
  const [latest] = [...reports].sort((a, b) => b.timestamp - a.timestamp);
  const nextMeetup = latest && latest.nextMeetup instanceof Date
    ? Utilities.formatDate(latest.nextMeetup, zones.reports, 'yyyy-MM-dd')
    : null;

  return {
    id: Utilities.formatDate(row.timestamp, zones.applications, "yyyy-MM-dd'T'HH:mm:ss"),
    name: clean(row.name),
    organizer: clean(row.organizer),
    country: clean(row.country),
    city: clean(row.city),
    format: clean(row.format),
    frequency: clean(frequency),
    links: row.links,
    next_meetup: nextMeetup,
    past_meetups: pastMeetups,
    ...geocode(row.city, row.country),
  };
}

/**
 * One entry per date, from the organizer's reports and the meetup's folder,
 * latest first.
 */
function pastMeetups(reports, folder, zones, photos) {
  const byDate = new Map();
  const at = date => {
    if (!byDate.has(date)) byDate.set(date, { date, title: null, photos: [], links: [] });
    return byDate.get(date);
  };

  [...reports]
    .filter(report => report.date instanceof Date)
    .sort((a, b) => a.timestamp - b.timestamp)
    .forEach(report => {
      const meetup = at(Utilities.formatDate(report.date, zones.reports, 'yyyy-MM-dd'));
      meetup.title = clean(report.title) || meetup.title;
      meetup.links.push(
        ...report.recording.map(url => ({ title: 'Recording', url })),
        ...report.blog.map(url => ({ title: 'Blog post', url })));
    });

  if (folder) {
    filesIn(folder.getFolders()).forEach(dateFolder => {
      const date = folderDate(dateFolder.getName());
      if (!date) {
        console.warn(`"${folder.getName()}/${dateFolder.getName()}" isn't a date, skipped`);
        return;
      }
      const meetup = at(date);
      filesIn(dateFolder.getFiles()).forEach(file => {
        if (file.getMimeType().startsWith('image/')) {
          const photo = photos.add(file);
          if (photo) meetup.photos.push(photo);
        } else if (file.getMimeType() === MimeType.GOOGLE_DOCS) {
          meetup.links.push(...docLinks(file));
        }
      });
    });
  }

  return [...byDate.values()]
    .map(meetup => ({ ...meetup, links: uniqueBy(meetup.links, link => link.url) }))
    .sort((a, b) => b.date.localeCompare(a.date));
}

/**
 * The meetup folders by name. take() hands out the folder named like a
 * meetup, unused() lists the ones nobody took.
 */
function meetupFolders() {
  const folders = new Map(filesIn(DriveApp.getFolderById(PAST_MEETUPS_FOLDER_ID).getFolders())
    .map(folder => [words(folder.getName()).join(' '), folder]));
  const taken = new Set();
  return {
    take(name) {
      const key = words(name).join(' ');
      if (!key || !folders.has(key)) return null;
      taken.add(key);
      return folders.get(key);
    },
    unused() {
      return [...folders].filter(([key]) => !taken.has(key)).map(([, folder]) => folder.getName());
    },
  };
}

function folderDate(name) {
  const iso = clean(name).match(/^\d{4}-\d{2}-\d{2}/);
  if (iso) return iso[0];
  const date = new Date(clean(name));
  return isNaN(date) ? null : Utilities.formatDate(date, Session.getScriptTimeZone(), 'yyyy-MM-dd');
}

/**
 * Photos already in the repository are kept as they are; new ones are
 * resized by Drive's thumbnailer and uploaded as git blobs, a few per run so a
 * run stays within Apps Script's time limit. changes() also removes the
 * photos that are no longer in Drive.
 */
function photoUploader(repository) {
  const published = new Map([...repository.files.keys()]
    .filter(path => path.startsWith(`${PHOTOS_PATH}/`))
    .map(path => [path.slice(PHOTOS_PATH.length + 1).replace(/\.\w+$/, ''), path]));
  const kept = new Set();
  const uploads = [];
  let postponed = 0;

  return {
    add(file) {
      const id = file.getId();
      let path = published.get(id);
      if (!path) {
        if (uploads.length >= NEW_PHOTOS_PER_RUN) {
          postponed++;
          return null;
        }
        const image = resized(file);
        if (!image) return null;
        path = `${PHOTOS_PATH}/${id}.${image.getContentType() === 'image/png' ? 'png' : 'jpg'}`;
        const blob = github('post', '/git/blobs', { content: Utilities.base64Encode(image.getBytes()), encoding: 'base64' });
        uploads.push({ path, mode: '100644', type: 'blob', sha: blob.sha });
      }
      kept.add(path);
      return path.replace(/^resources\/public/, '');
    },
    postponed: () => postponed,
    changes() {
      const removed = [...published.values()]
        .filter(path => !kept.has(path))
        .map(path => ({ path, mode: '100644', type: 'blob', sha: null }));
      return [...uploads, ...removed];
    },
  };
}

function resized(file) {
  const headers = { Authorization: `Bearer ${ScriptApp.getOAuthToken()}` };
  const metadata = UrlFetchApp.fetch(
    `https://www.googleapis.com/drive/v3/files/${file.getId()}?fields=thumbnailLink&supportsAllDrives=true`,
    { headers });
  const { thumbnailLink } = JSON.parse(metadata.getContentText());
  if (!thumbnailLink) {
    console.warn(`Drive has no preview of "${file.getName()}" yet, trying again next run`);
    return null;
  }
  const image = UrlFetchApp.fetch(thumbnailLink.replace(/=s\d+$/, `=s${PHOTO_SIZE}`), { headers, muteHttpExceptions: true });
  return image.getResponseCode() === 200 ? image.getBlob() : null;
}

/**
 * Every link in a Google Doc. Its title is the link's text, or the words
 * before it on the same line: "Slides: https://..." is titled Slides.
 */
function docLinks(file) {
  const html = UrlFetchApp.fetch(
    `https://www.googleapis.com/drive/v3/files/${file.getId()}/export?mimeType=text/html&supportsAllDrives=true`,
    { headers: { Authorization: `Bearer ${ScriptApp.getOAuthToken()}` } }).getContentText();

  return (html.match(/<p[^>]*>.*?<\/p>/gs) || []).flatMap(paragraph =>
    [...paragraph.matchAll(/<a [^>]*href="([^"]+)"[^>]*>(.*?)<\/a>/gs)].map(link => {
      const text = htmlText(link[2]);
      const before = htmlText(paragraph.slice(0, link.index).split('</a>').pop()).replace(/[\s:\-]+$/, '');
      return {
        title: (urlsIn(text).length ? before : text) || null,
        url: withoutGoogleRedirect(htmlText(link[1])),
      };
    }));
}

function withoutGoogleRedirect(url) {
  const target = url.match(/^https:\/\/www\.google\.com\/url\?q=([^&]+)/);
  return target ? decodeURIComponent(target[1]) : url;
}

function htmlText(html) {
  return clean(html
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/g, ' ')
    .replace(/&#(\d+);/g, (_, code) => String.fromCharCode(code))
    .replace(/&quot;/g, '"')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&'));
}

/**
 * Same email, or one name contains every word of the other, so that
 * "Kaaran Tham Edward Hughes" is the same organizer as "Edward Hughes".
 */
function sameOrganizer(report, application) {
  const email = clean(report.email).toLowerCase();
  if (email && email === clean(application.email).toLowerCase()) return true;

  const [shorter, longer] = [words(report.organizer), words(application.organizer)]
    .sort((a, b) => a.length - b.length);
  return shorter.length > 1 && shorter.every(word => longer.includes(word));
}

/**
 * Coordinates for the map, without the area in parentheses. Cached in the
 * script properties, so each place is looked up once and the published file
 * doesn't change from run to run.
 */
function geocode(city, country) {
  const place = [clean(String(city).replace(/\(.*?\)/g, '')), clean(country)].filter(Boolean).join(', ');
  if (!place) return {};

  const properties = PropertiesService.getScriptProperties();
  const key = `geocode:${place}`;
  const cached = properties.getProperty(key);
  if (cached) return JSON.parse(cached);

  const [result] = Maps.newGeocoder().geocode(place).results;
  const point = result
    ? { lat: round(result.geometry.location.lat), lng: round(result.geometry.location.lng) }
    : {};
  properties.setProperty(key, JSON.stringify(point));
  return point;
}

/**
 * The head of BRANCH and the blob sha of every file in it, to tell what
 * changed without downloading anything.
 */
function readRepository() {
  const head = github('get', `/git/ref/heads/${BRANCH}`).object.sha;
  const tree = github('get', `/git/commits/${head}`).tree.sha;
  const { tree: entries, truncated } = github('get', `/git/trees/${tree}?recursive=1`);
  if (truncated) throw new Error('The repository tree is too big to read in one go');
  return { head, tree, files: new Map(entries.map(entry => [entry.path, entry.sha])) };
}

/** All the changes in one commit, so Vercel deploys once. */
function commit(repository, changes, message) {
  const tree = github('post', '/git/trees', { base_tree: repository.tree, tree: changes });
  const created = github('post', '/git/commits', { message, tree: tree.sha, parents: [repository.head] });
  github('patch', `/git/refs/heads/${BRANCH}`, { sha: created.sha });
}

function github(method, path, body) {
  const token = PropertiesService.getScriptProperties().getProperty('GITHUB_TOKEN');
  if (!token) throw new Error('Set the GITHUB_TOKEN script property first');

  const options = {
    method,
    headers: { Authorization: `Bearer ${token}`, Accept: 'application/vnd.github+json' },
    muteHttpExceptions: true,
  };
  if (body) {
    options.contentType = 'application/json';
    options.payload = JSON.stringify(body);
  }
  const response = UrlFetchApp.fetch(`https://api.github.com/repos/${REPOSITORY}${path}`, options);
  if (response.getResponseCode() >= 300) {
    throw new Error(`GitHub answered ${response.getResponseCode()} to ${method.toUpperCase()} ${path}: ${response.getContentText()}`);
  }
  return JSON.parse(response.getContentText());
}

/** The id git gives a file, to compare it with the published one. */
function gitBlobSha(text) {
  const bytes = Utilities.newBlob('').setDataFromString(text, 'UTF-8').getBytes();
  const header = Utilities.newBlob(`blob ${bytes.length}\0`).getBytes();
  return Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_1, header.concat(bytes))
    .map(byte => (byte & 0xff).toString(16).padStart(2, '0'))
    .join('');
}

function isYes(value) {
  return /^(yes|y|s[ií])$/i.test(clean(value));
}

function urlsIn(value) {
  return (String(value || '').match(/https?:\/\/[^\s"'<>]+/g) || [])
    .map(url => url.replace(/[.,;)]+$/, ''));
}

function words(name) {
  return clean(name).toLowerCase().normalize('NFD').replace(/\p{M}/gu, '')
    .split(/[^a-z]+/).filter(Boolean);
}

function clean(value) {
  return String(value == null ? '' : value).replace(/\s+/g, ' ').trim();
}

function unique(values) {
  return [...new Set(values)];
}

function uniqueBy(values, key) {
  return [...new Map(values.map(value => [key(value), value])).values()];
}

/** A Drive iterator as an array, sorted by name so runs are repeatable. */
function filesIn(iterator) {
  const files = [];
  while (iterator.hasNext()) files.push(iterator.next());
  return files.sort((a, b) => a.getName().localeCompare(b.getName()));
}

function round(n) {
  return Math.round(n * 10000) / 10000;
}
