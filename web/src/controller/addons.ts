export interface AddonAction {id:string;label:string}
export function addonActions(value:unknown):AddonAction[] {
  const actions=(value as {actions?:unknown})?.actions;
  if(!Array.isArray(actions)||actions.length>16)return [];
  const ids=new Set<string>(), result:AddonAction[]=[];
  for(const action of actions){
    if(!action || typeof action.id!=='string' || action.id.length>180 || !/^addon:[A-Za-z0-9_.:/-]+$/.test(action.id) || ids.has(action.id)
      || typeof action.label!=='string' || action.label.length>80)return [];
    ids.add(action.id);result.push({id:action.id,label:action.label});
  }
  return result;
}
