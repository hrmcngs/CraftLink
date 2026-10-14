export class Link {
  private socket?: WebSocket;
  private retry?: number;
  private stopped = false;
  private pingAt = 0;
  private lastPong = 0;
  ready = false;
  private videoEnabled = true;
  constructor(private token: string, private status: (text: string) => void, private reset: () => void, private preferences: (value: unknown) => void = () => {}, private frame: (blob: Blob) => Promise<number | undefined> = async () => undefined) {}
  connect() {
    if (this.stopped) return;
    this.status('接続中…');
    const ws = this.socket = new WebSocket(`ws://${location.host}/ws`);
    ws.onopen = () => { this.lastPong = performance.now(); ws.send(JSON.stringify({type: 'hello', token: this.token})); };
    ws.onmessage = async e => {
      if (typeof e.data !== 'string') {
        try {
          const id=await this.frame(e.data instanceof Blob ? e.data : new Blob([e.data]));
          if (id!==undefined && this.socket===ws && this.ready) ws.send(JSON.stringify({type:'frameAck',frame:id}));
        } catch { this.status('映像の受信に失敗しました'); }
        return;
      }
      const data = JSON.parse(e.data);
      if (data.type === 'preferences') { this.preferences(data); return; }
      if (data.type === 'revoked') { this.disconnect(); this.status('PCで接続が解除されました'); return; }
      if (data.type === 'pending') this.status('PCでF8を押し、接続を許可してください');
      if (data.type === 'approved') { this.ready = true; this.status('接続済み'); ws.send(JSON.stringify({type:'video',enabled:this.videoEnabled})); }
      if (data.type === 'pong') { this.lastPong = performance.now(); if (this.ready) this.status(`接続済み · ${Math.round(this.lastPong - this.pingAt)} ms`); }
    };
    ws.onclose = () => {
      this.ready = false; this.reset();
      if (!this.stopped) { this.status('切断 · 再接続中（期限切れの場合はPCで再開始）'); this.retry = window.setTimeout(() => this.connect(), 2000); }
    };
    ws.onerror = () => ws.close();
  }
  send(data: unknown) { if (this.ready && this.socket?.readyState === WebSocket.OPEN) this.socket.send(JSON.stringify(data)); }
  setVideoEnabled(enabled:boolean) { this.videoEnabled=enabled; this.send({type:'video',enabled}); }
  heartbeat() {
    if (this.socket?.readyState !== WebSocket.OPEN) return;
    if (performance.now() - this.lastPong > 5000) { this.socket.close(); return; }
    this.pingAt = performance.now(); this.socket.send(JSON.stringify({type: 'ping'}));
  }
  disconnect() { this.stopped = true; clearTimeout(this.retry); this.send({type: 'disconnect'}); this.socket?.close(); this.ready = false; this.reset(); this.status('切断しました · 再接続にはページを再読み込み'); }
}
