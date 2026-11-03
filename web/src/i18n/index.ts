import english from './en.json';
export type Language = 'ja' | 'en';
const dictionary: Record<string,string> = english;
export function initialLanguage(saved: string | null, browser: string): Language {
  return saved === 'ja' || saved === 'en' ? saved : browser.toLowerCase().startsWith('ja') ? 'ja' : 'en';
}
export function translate(value: string, language: Language): string {
  if (language === 'ja') return value;
  const trimmed = value.trim();
  const ready = /^うごかせるよ · (\d+) ms$/.exec(trimmed);
  if (ready) return value.replace(trimmed, 'Ready to play · ' + ready[1] + ' ms');
  const translated = dictionary[trimmed];
  return translated ? value.replace(trimmed, translated) : value;
}
// Only presentation nodes are translated. Protocol values, input values, names and URLs stay intact.
export function installLanguagePicker(root: HTMLElement): void {
  let saved: string | null = null;
  try { saved = localStorage.getItem('craftlink.language'); } catch { /* Private browsing */ }
  let language = initialLanguage(saved, navigator.language);
  const originals = new WeakMap<Node, {source:string; rendered:string}>();
  const attributes = new WeakMap<Element, Map<string,{source:string;rendered:string}>>();
  const label = document.createElement('label');
  label.textContent = 'Language / 言語 ';
  const select = document.createElement('select');
  select.setAttribute('aria-label', 'Language / 言語');
  select.innerHTML = '<option value="ja">日本語</option><option value="en">English</option>';
  select.value = language; label.append(select);
  (root.querySelector('#touch-options') ?? root.querySelector('dialog') ?? root).prepend(label);
  function render(): void {
    observer.disconnect();
    document.documentElement.lang = language;
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    let node: Node | null;
    while ((node = walker.nextNode())) {
      if (node.parentElement?.closest('script,style,input,textarea,[data-skin],#identity,#inventory-player-name,#chat-messages,#bot-chat ul')) continue;
      const current = node.textContent ?? '';
      const prior = originals.get(node);
      const source = prior && prior.rendered === current ? prior.source : current;
      const rendered = translate(source, language);
      if (rendered !== current) node.textContent = rendered;
      originals.set(node, {source,rendered});
    }
    for (const element of Array.from(root.querySelectorAll('[aria-label],[placeholder],[title]'))) {
      const entries = attributes.get(element) ?? new Map();
      for (const name of ['aria-label','placeholder','title']) {
        const current = element.getAttribute(name); if (current === null) continue;
        const prior = entries.get(name);
        const source = prior && prior.rendered === current ? prior.source : current;
        const rendered = translate(source, language);
        if (current !== rendered) element.setAttribute(name, rendered);
        entries.set(name,{source,rendered});
      }
      attributes.set(element,entries);
    }
    observer.observe(root,{subtree:true,childList:true,characterData:true,attributes:true,attributeFilter:['aria-label','placeholder','title']});
  }
  let queued = false;
  const observer = new MutationObserver(() => {
    if (queued) return; queued = true;
    queueMicrotask(() => {queued = false; render();});
  });
  select.onchange = () => {
    language = select.value === 'ja' ? 'ja' : 'en';
    try {localStorage.setItem('craftlink.language',language);} catch { /* Optional storage */ }
    render();
  };
  render();
}
