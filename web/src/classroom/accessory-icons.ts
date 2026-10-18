const outlines:Record<string,string>={
 head:'<path d="M5 18V8l4-4h6l4 4v10h-4v-6H9v6z"/>',
 necklace:'<path d="M5 5h14l2 5-9 11L3 10z"/><circle cx="12" cy="14" r="2"/>',
 body:'<path d="m7 4 5 3 5-3 5 6-4 3-1-2v10H7V11l-1 2-4-3z"/>',
 back:'<path d="M6 6h12v15H6zM9 6V3h6v3"/>',
 ring:'<circle cx="12" cy="13" r="8"/><path d="m8 5 4-3 4 3"/>',
 belt:'<path d="M2 9h20v7H2zM9 8h6v9H9z"/>',
 hands:'<path d="M6 20 3 13l2-2 3 3V5h3v7-9h3v9-7h3v8-5h3v8l-5 5z"/>',
 charm:'<circle cx="12" cy="13" r="7"/><path d="M10 6V2h4v4m-2 3v8m-4-4h8"/>',
};
export function accessoryPlaceholder(label:string){
 const kind=label.toLowerCase().split(/\s+/)[0];
 const aliases:Record<string,string>={headwear:'head',neck:'necklace',chest:'body',bracelet:'ring',hand:'hands'};
 return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linejoin="miter" aria-hidden="true">${outlines[aliases[kind]||kind]||outlines.charm}</svg>`;
}
