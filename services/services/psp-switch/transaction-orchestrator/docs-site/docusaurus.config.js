// @ts-check
const { themes } = require('prism-react-renderer');

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'PSP Orchestrator — TDD Test Suite',
  tagline: 'Dual-Direction UPI Transaction Orchestrator · 43 Tests · 0 Failures',
  favicon: 'img/favicon.ico',
  url: 'http://localhost',
  baseUrl: '/',
  onBrokenLinks: 'warn',
  onBrokenMarkdownLinks: 'warn',
  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          sidebarPath: require.resolve('./sidebars.js'),
          routeBasePath: '/',
        },
        blog: false,
        theme: {
          customCss: require.resolve('./src/css/custom.css'),
        },
      }),
    ],
  ],

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      colorMode: {
        defaultMode: 'dark',
        disableSwitch: false,
      },
      navbar: {
        title: '🏦 PSP Orchestrator Docs',
        items: [
          { to: '/', label: 'Introduction', position: 'left' },
          { to: '/test-architecture', label: 'Architecture', position: 'left' },
          { to: '/send-flow', label: 'SEND Flow', position: 'left' },
          { to: '/collect-flow', label: 'COLLECT Flow', position: 'left' },
          { to: '/edge-cases', label: 'Edge Cases', position: 'left' },
          { to: '/response-codes', label: 'Response Codes', position: 'left' },
        ],
      },
      prism: {
        theme: themes.nightOwl,
        darkTheme: themes.nightOwl,
        additionalLanguages: ['java', 'json', 'bash'],
      },
      footer: {
        style: 'dark',
        copyright: `PSP Transaction Orchestrator · TDD Documentation · Built with Docusaurus`,
      },
    }),
};

module.exports = config;
