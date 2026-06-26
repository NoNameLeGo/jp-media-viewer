const http = require('http');
const options = {
    hostname: 'localhost',
    port: 9222,
    path: '/json',
    method: 'GET'
};

const req = http.request(options, (res) => {
    let data = '';
    res.on('data', chunk => data += chunk);
    res.on('end', () => {
        const tabs = JSON.parse(data);
        const chaoxingTab = tabs.find(t => t.url.includes('mooc1.chaoxing.com'));
        if (chaoxingTab) {
            console.log('Found tab:', chaoxingTab.id);
            console.log('WebSocket URL:', chaoxingTab.webSocketDebuggerUrl);
        } else {
            console.log('Tab not found');
        }
    });
});

req.on('error', (e) => {
    console.error(e.message);
});

req.end();
