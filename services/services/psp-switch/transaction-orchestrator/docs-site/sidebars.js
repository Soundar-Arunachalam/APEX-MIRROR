/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
    tutorialSidebar: [
        { type: 'doc', id: 'intro', label: '📖 Introduction to TDD' },
        { type: 'doc', id: 'test-architecture', label: '🏗️ Test Architecture' },
        { type: 'doc', id: 'send-flow', label: '📤 SEND Flow Tests' },
        { type: 'doc', id: 'collect-flow', label: '📥 COLLECT Flow Tests' },
        { type: 'doc', id: 'edge-cases', label: '⚠️ Edge Cases & Validation' },
        { type: 'doc', id: 'response-codes', label: '📋 Response Codes Reference' },
    ],
};

module.exports = sidebars;
