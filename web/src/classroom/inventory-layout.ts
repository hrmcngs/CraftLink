const equipmentNames:Record<number,string>={39:'頭',38:'胴',37:'脚',36:'足',40:'オフハンド'};
function slot(index:number){
 const label=equipmentNames[index];
 return `<button type="button" data-bot-slot="${index}" aria-label="${label||'持ち物 '+(index+1)}"><span></span><small></small>${label?`<em>${index===40?'左手':label}</em>`:''}</button>`;
}
export function inventoryMarkup(){return `<dialog id="bot-inventory" aria-labelledby="inventory-title">
 <div class="inventory-heading"><h1 id="inventory-title">インベントリ</h1><button id="bot-inventory-close" aria-label="持ち物を閉じる">×</button></div>
 <nav class="inventory-mobile-tabs" aria-label="インベントリの表示"><button data-inventory-view="inventory" aria-pressed="true">持ち物・装備</button><button data-inventory-view="creative" aria-pressed="false">クリエイティブ</button></nav>
 <div class="inventory-workspace">
  <section id="creative-pane" class="inventory-panel" aria-label="アイテム一覧">
   <div class="inventory-tabs"><button id="tab-inventory" aria-pressed="true">持ち物</button><button id="tab-creative" aria-pressed="false" disabled>クリエイティブ</button></div>
   <nav id="creative-tab-strip" aria-label="Modのクリエイティブタブ"></nav>
   <label class="catalog-category">カテゴリ <select id="creative-category" aria-label="クリエイティブタブ"></select></label>
   <p id="creative-locked">クリエイティブにすると、ここからアイテムを選べます。</p>
   <div id="creative-items"></div>
   <nav id="creative-pages"><button id="creative-prev">◀</button><output id="creative-page">アイテム一覧</output><button id="creative-next">▶</button></nav>
   <p id="creative-status" role="status"></p>
  </section>
  <section id="inventory-player" class="inventory-panel" aria-label="装備と持ち物">
   <aside id="inventory-accessories" aria-label="アクセサリー" hidden></aside>
   <div class="inventory-body">
   <div class="inventory-character">
    <div id="inventory-armor" aria-label="防具">${[39,38,37,36].map(slot).join('')}</div>
    <figure class="inventory-avatar" title="選択したアイテムをここにタップして捨てる"><img id="inventory-appearance" alt="装備を反映したBOT" hidden><canvas id="inventory-skin" width="64" height="128" aria-label="自分のスキンの正面プレビュー"></canvas><figcaption id="inventory-player-name">あなたのBOT</figcaption></figure>
    <div class="inventory-side"><div class="inventory-offhand">${slot(40)}</div><p id="inventory-selection" role="status">アイテムを選択</p></div>
   </div>
   <div id="bot-inventory-grid" aria-label="バックパック">${Array.from({length:27},(_,i)=>slot(i+9)).join('')}</div>
   <div id="inventory-hotbar" aria-label="ホットバー">${Array.from({length:9},(_,i)=>slot(i)).join('')}</div>
   <button id="inventory-open-backpack" type="button" hidden>バックパックを開く</button>
   <details class="inventory-more"><summary>詳細操作</summary><div id="inventory-actions"><button id="inventory-equip">手に持つ</button><select id="equipment-target" aria-label="装備先"><option value="39">頭</option><option value="38">胴</option><option value="37">脚</option><option value="36">足</option><option value="40">オフハンド</option></select><button id="inventory-wear">装備 / 外す</button><button id="inventory-drop-one">1個捨てる</button><button id="inventory-drop-stack">全部捨てる</button></div></details>
   <p class="inventory-help">アイテムをタップ → 移動先をタップ。長押しで個数を調整、2回タップで半分。防具・オフハンドも同じ操作。</p>
   </div>
  </section>
 </div>
 </dialog>`;}
