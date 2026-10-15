export const skinChoices=[['auto','自動'],['steve','Steve'],['alex','Alex'],['ari','Ari'],['efe','Efe'],['kai','Kai'],['makena','Makena'],['noor','Noor'],['sunny','Sunny'],['zuri','Zuri']] as const;
export interface ParticipantProfile {name:string;skin:string}
export function validName(name:string){const normalized=name.trim().normalize('NFC');return [...normalized].length<=16&&/^[\p{L}\p{Nd} _-]*$/u.test(normalized);}
export function normalizeProfile(value:unknown):ParticipantProfile {
 const data=typeof value==='object'&&value!==null?value as Record<string,unknown>:{};
 return {name:typeof data.name==='string'&&data.name.length<=64&&validName(data.name)?data.name.trim().normalize('NFC'):'',skin:skinChoices.some(([id])=>id===data.skin)?data.skin as string:'auto'};
}
export function loadProfile():ParticipantProfile{try{return normalizeProfile(JSON.parse(localStorage.getItem('craftlink.botProfile')??'null'));}catch{return normalizeProfile(null);}}
export function saveProfile(value:ParticipantProfile){try{localStorage.setItem('craftlink.botProfile',JSON.stringify(normalizeProfile(value)));}catch{}}
