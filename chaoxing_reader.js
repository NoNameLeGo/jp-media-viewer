const WebSocket = require('ws');
const http = require('http');

// 先找到超星标签页
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
        
        console.log('找到超星页面:', chaoxingTab.title);
        console.log('WebSocket URL:', chaoxingTab.webSocketDebuggerUrl);
        
        // 建立 WebSocket 连接
        const ws = new WebSocket(chaoxingTab.webSocketDebuggerUrl);
        
        ws.on('open', async () => {
            console.log('已连接到 Chrome DevTools Protocol');
            
            // 启用 DOM 和 Page 域
            ws.send(JSON.stringify({ id: 1, method: 'DOM.enable' }));
            ws.send(JSON.stringify({ id: 2, method: 'Page.enable' }));
            ws.send(JSON.stringify({ id: 3, method: 'Runtime.enable' }));
            
            // 等待一小段时间让命令生效
            await new Promise(resolve => setTimeout(resolve, 500));
            
            // 获取页面文本内容
            ws.send(JSON.stringify({
                id: 4,
                method: 'Runtime.evaluate',
                params: {
                    expression: 'document.body.innerText.substring(0, 10000)'
                }
            }));
            
            // 监听响应
            ws.on('message', (msg) => {
                const response = JSON.parse(msg);
                if (response.id === 4 && response.result) {
                    const content = response.result.result.value || '';
                    console.log('\\n=== 页面内容 ===');
                    console.log(content);
                    ws.close();
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
