#!/usr/bin/env node
const fs = require('fs');
const path = require('path');

const packageJson = require('../package.json');

const versionInfo = {
  version: packageJson.version,
  buildDate: new Date().toISOString(),
  buildTimestamp: Date.now()
};

const outputPath = path.join(__dirname, '../public/version.json');

fs.writeFileSync(outputPath, JSON.stringify(versionInfo, null, 2));

console.log('Generated version.json:', versionInfo);
