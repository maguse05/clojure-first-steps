/**
 * Bound to the "Clojure Meetup Pilot Program Application (Responses)"
 * spreadsheet. It does two things for the website:
 *
 * syncMeetups runs every hour and publishes the approved meetups (Yes under
 * "Sponsorship Setup?") to resources/meetups-sheet.json in the website's
 * repository. Vercel redeploys the site on every commit to main, and nothing is
 * committed when nothing changed. Only public fields leave the spreadsheet:
 * no emails, scores, grants or review notes. The next meetup date comes from
 * the latest Post-Event Report of the same organizer, matched on email when the
 * report has one, otherwise on name.
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
 */

const REPOSITORY = 'maguse05/clojure-first-steps';
const BRANCH = 'main';
const FILE_PATH = 'resources/meetups-sheet.json';

const APPLICATIONS_SHEET = 'Form Responses 1';
const REPORTS_SPREADSHEET_ID = '11FZuqHJ0i4lz6cQtjKDT09uSXU7-oRyN-C6c73Srzjc';
const REPORTS_SHEET = 'Form Responses 1';

const TEAM_EMAIL = 'clojure-meetups@nubank.com.br';

const APPLICATION_COLUMNS = {
  timestamp: 'Timestamp',
  email: 'Email address',
  organizer: 'Full Name',
  name: 'Final Meetup name',
  format: 'Meetup format',
  country: 'Country',
  city: 'Where do you plan to hold the meetup? (City and country)',
  frequency: 'How frequently do you plan to hold the meetup?',
  otherFrequency: "If 'Other' in the previous question, please specify your planned frequency.",
  approved: 'Sponsorship Setup?',
  links: 'Generic Meetup Link',
};

const PROPOSED_NAME = 'Name of the Clojure Meetup?';

const REPORT_COLUMNS = {
  timestamp: 'Timestamp',
  email: 'Email Address',
  organizer: "Meetup Organizer's Full Name",
  nextMeetup: 'When do you expect the next meetup to be held?',
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
  const reports = readRows(reportsSpreadsheet.getSheetByName(REPORTS_SHEET), REPORT_COLUMNS);
  const zones = {
    applications: spreadsheet.getSpreadsheetTimeZone(),
    reports: reportsSpreadsheet.getSpreadsheetTimeZone(),
  };

  const meetups = readRows(applications, APPLICATION_COLUMNS, ['links'])
    .filter(row => isYes(row.approved))
    .map(row => toMeetup(row, latestReport(reports, row), zones));

  const content = JSON.stringify({ meetups }, null, 2) + '\n';
  if (commitIfChanged(content)) {
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
  const headers = sheet.getRange(1, 1, 1, sheet.getLastColumn()).getValues()[0].map(h => String(h).trim());
  const lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    sheet.appendRow(headers.map(header =>
      header === APPLICATION_COLUMNS.timestamp ? new Date() : asText(answers[header])));
  } finally {
    lock.releaseLock();
  }

  notifyTeam(answers);
  return respond({ ok: true });
}

function notifyTeam(answers) {
  const name = answers[PROPOSED_NAME] || 'unnamed';
  MailApp.sendEmail({
    to: TEAM_EMAIL,
    subject: `New meetup application: ${name}`,
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
  const position = header => headers.findIndex(h => String(h).trim() === header);

  return values
    .map((cells, i) => Object.fromEntries(Object.entries(columns).map(([key, header]) => {
      const column = position(header);
      if (column < 0) return [key, linkColumns.includes(key) ? [] : ''];
      if (!linkColumns.includes(key)) return [key, cells[column]];

      const text = richText[i][column];
      const linked = text ? text.getRuns().map(run => run.getLinkUrl()) : [];
      return [key, unique([cells[column], formulas[i][column], ...linked].flatMap(urlsIn))];
    })))
    .filter(row => row.timestamp instanceof Date);
}

function toMeetup(row, report, zones) {
  const frequency = clean(row.frequency).startsWith('Other') ? row.otherFrequency : row.frequency;
  const nextMeetup = report && report.nextMeetup instanceof Date
    ? Utilities.formatDate(report.nextMeetup, zones.reports, 'yyyy-MM-dd')
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
    ...geocode(row.city, row.country),
  };
}

function latestReport(reports, application) {
  return reports
    .filter(report => sameOrganizer(report, application))
    .sort((a, b) => b.timestamp - a.timestamp)[0];
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
 * Coordinates for the map. Cached in the script properties, so each place is
 * looked up once and the published file doesn't change from run to run.
 */
function geocode(city, country) {
  const place = [clean(city), clean(country)].filter(Boolean).join(', ');
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

function commitIfChanged(content) {
  const token = PropertiesService.getScriptProperties().getProperty('GITHUB_TOKEN');
  if (!token) throw new Error('Set the GITHUB_TOKEN script property first');

  const url = `https://api.github.com/repos/${REPOSITORY}/contents/${FILE_PATH}`;
  const headers = { Authorization: `Bearer ${token}`, Accept: 'application/vnd.github+json' };
  const current = UrlFetchApp.fetch(`${url}?ref=${BRANCH}`, { headers, muteHttpExceptions: true });

  let sha;
  if (current.getResponseCode() === 200) {
    const file = JSON.parse(current.getContentText());
    const published = Utilities.newBlob(Utilities.base64Decode(file.content.replace(/\n/g, ''))).getDataAsString('UTF-8');
    if (published === content) return false;
    sha = file.sha;
  } else if (current.getResponseCode() !== 404) {
    throw new Error(`GitHub answered ${current.getResponseCode()}: ${current.getContentText()}`);
  }

  UrlFetchApp.fetch(url, {
    method: 'put',
    headers,
    contentType: 'application/json',
    payload: JSON.stringify({
      message: 'Sync meetups from the applications sheet',
      content: Utilities.base64Encode(content, Utilities.Charset.UTF_8),
      branch: BRANCH,
      sha,
    }),
  });
  return true;
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

function round(n) {
  return Math.round(n * 10000) / 10000;
}
