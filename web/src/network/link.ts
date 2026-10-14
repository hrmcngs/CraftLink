export class Link {
  private socket?: WebSocket;
  private retry?: number;
  private stopped = false;
  private pingAt = 0;
  private lastPong = 0;
  ready = false;
  constructor(private token: string, private status: (text: string) => void, private reset: () => void) {}
  connect() {
    if (this.stopped) return;
    this.status('接続中…');
    const ws = this.socket = new WebSocket(`ws://${location.host}/ws`);
    ws.onopen = () => { this.lastPong = performance.now(); ws.send(JSON.stringify({type: 'hello', token: this.token})); };
    ws.onmessage = e => {
      const data = JSON.parse(e.data);
      if (data.type === 'revoked') { this.disconnect(); this.status('PCで接続が解除されました'); return; }
      if (data.type === 'pending') this.status('PCでF8を押し、接続を許可してください');
      if (data.type === 'approved') { this.ready = true; this.status('接続済み'); }
      if (data.type === 'pong') { this.lastPong = performance.now(); if (this.ready) this.status(`接続済み · ${Math.round(this.lastPong - this.pingAt)} ms`); }
    };
    ws.onclose = () => {
      this.ready = false; this.reset();
      if (!this.stopped) { this.status('切断 · 再接続中（期限切れの場合はPCで再開始）'); this.retry = window.setTimeout(() => this.connect(), 2000); }
    };
    ws.onerror = () => ws.close();
  }
  send(data: unknown) { if (this.ready && this.socket?.readyState === WebSocket.OPEN) this.socket.send(JSON.stringify(data)); }
  heartbeat() {
    if (this.socket?.readyState !== WebSocket.OPEN) return;
    if (performance.now() - this.lastPong > 5000) { this.socket.close(); return; }
    this.pingAt = performance.now(); this.socket.send(JSON.stringify({type: 'ping'}));
  }
  disconnect() { this.stopped = true; clearTimeout(this.retry); this.send({type: 'disconnect'}); this.socket?.close(); this.ready = false; this.reset(); this.status('切断しました · 再接続にはページを再読み込み'); }
}
