/** Use TLS WebSockets when the controller itself was opened through an HTTPS tunnel. */
export function websocketUrl(page: {protocol:string;host:string}, path:string) {
  if(page.protocol!=='http:'&&page.protocol!=='https:')throw new Error('Open the controller through its QR URL');
  return `${page.protocol==='https:'?'wss:':'ws:'}//${page.host}${path}`;
}
