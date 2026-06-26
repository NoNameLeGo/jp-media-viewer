const WebSocket = require('ws');
const http = require('http');

const req = http.get('http://localhost:9222/json', (res) => {
    let data = '';
    res.on('data', chunk => data += chunk);
    res.on('end', async () => {
        const tabs = JSON.parse(data);
        const chaoxingTab = tabs.find(t => t.url.includes('mooc1.chaoxing.com'));
        if (!chaoxingTab) {
            console.log('超星页面未找到');
            return;
        }
        
        const ws = new WebSocket(chaoxingTab.webSocketDebuggerUrl);
        
        ws.on('open', async () => {
            ws.send(JSON.stringify({ id: 1, method: 'Runtime.enable' }));
            ws.send(JSON.stringify({ id: 2, method: 'DOM.enable' }));
            ws.send(JSON.stringify({ id: 3, method: 'Page.enable' }));
            
            await new Promise(resolve => setTimeout(resolve, 500));
            
            // 获取完整页面HTML
            ws.send(JSON.stringify({
                id: 4,
                method: 'Runtime.evaluate',
                params: {
                    expression: 'document.documentElement.outerHTML.substring(0, 50000)'
                }
            }));
            
            // 获取页面中的所有文本
            ws.send(JSON.stringify({
                id: 5,
                method: 'Runtime.evaluate',
                params: {
                    expression: 'document.body.innerText'
                }
            }));
            
            ws.on('message', (msg) => {
                const response = JSON.parse(msg);
                if ((response.id === 4 || response.id === 5) && response.result) {
                    const content = response.result.result.value || '';
                    console.log('=== ID:', response.id, '===');
                    console.log(content);
                }
            });
        });
        
        ws.on('error', (err) => {
            console.error('WebSocket 错误:', err.message);
        });
    });
});

req.on('error', (e) => {
    console.error('请求错误:', e.message);
});
