// Karma configuration for HELIX unit tests.
//
// Run with:
//   npm test -- --watch=false --browsers=ChromeHeadless
//
// CHROME_BIN is redirected to scripts/chromium-no-sandbox.sh, a thin wrapper that execs the
// real browser with --no-sandbox appended — required to launch headless Chrome while running
// as root (this sandbox, many CI containers) and a harmless no-op anywhere else. The wrapper
// itself resolves the real binary from HELIX_KARMA_CHROME_BIN (defaulted below from any
// CHROME_BIN the caller already set, e.g. /opt/pw-browsers/chromium here) or a PATH lookup.
const path = require('path');

process.env.HELIX_KARMA_CHROME_BIN = process.env.CHROME_BIN || process.env.HELIX_KARMA_CHROME_BIN || '';
process.env.CHROME_BIN = path.join(__dirname, 'scripts', 'chromium-no-sandbox.sh');

module.exports = function (config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage'),
      require('@angular-devkit/build-angular/plugins/karma'),
    ],
    client: {
      jasmine: {},
      clearContext: false,
    },
    jasmineHtmlReporter: {
      suppressAll: true,
    },
    coverageReporter: {
      dir: require('path').join(__dirname, './coverage/helix-web'),
      subdir: '.',
      reporters: [{ type: 'html' }, { type: 'text-summary' }],
    },
    reporters: ['progress', 'kjhtml'],
    port: 9876,
    colors: true,
    logLevel: config.LOG_INFO,
    autoWatch: true,
    restartOnFileChange: true,
  });
};
