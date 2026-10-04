"""Named commit/docs/primary source inspection only; no source tree export."""
import datetime
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import zipfile
sys.stdout.reconfigure(encoding='utf-8')
B=Path(__file__).resolve().parent
R=Path('D:/GitHub/AdvancedRocketry-Community')
C='3f3d62aed3980186fe0acc9592cf93ca436405fb'
F=Path('C:/Users/Administrator/.gradle/caches/forge_gradle/maven_downloader/net/minecraftforge/forge/1.20.1-47.4.10/forge-1.20.1-47.4.10-sources.jar')
M=Path('C:/Users/Administrator/.gradle/caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/1.20.1-47.4.10_mapped_official_1.20.1/forge-1.20.1-47.4.10_mapped_official_1.20.1.jar')
def meta(raw):return {'bytes':len(raw),'sha256':hashlib.sha256(raw).hexdigest()}
def git(args):
    p=subprocess.run(['git']+args,cwd=R,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=60)
    assert p.returncode==0,(args,p.stderr)
    return p.stdout
def lines(raw, ranges):
    txt=raw.decode('utf-8').splitlines();out=[]
    for a,b in ranges:
        out.extend({'line':i,'text':txt[i-1]} for i in range(a,min(b,len(txt))+1))
    return out
live=['AGENTS.md','PROJECT-CONFIG.md','PRODUCT.md','docs/01-PORTING-PRINCIPLES.md','docs/04-VERSION-ROADMAP.md',
      'docs/versions/V1.8.0-CLASSIC-CONTENT-COMPLETION.md','docs/05-MASTER-TEST-PLAN.md',
      'docs/06-RELEASE-AND-ACCEPTANCE-GATES.md','docs/16-POST-1.0-VERSION-ROADMAP.md',
      'docs/17-V1PLUS-QUALITY-BUDGETS.md','docs/14-PARALLEL-DEVELOPMENT-AND-WORKTREE-COORDINATION.md',
      'docs/11-RISK-REGISTER.md','docs/decisions/ADR-064-CLASSIC-MACHINES-FLUIDS-AND-COMPONENTS.md',
      'docs/work/v1.8.0-c16a-save-guard/TASK.md','docs/work/v1.8.0-c16a-save-guard/reviews/SOURCE-REVIEW-01.md',
      'docs/work/v1.8.0-c16a-save-guard/reviews/NATIVE-CORRECTION-03.md']
inputs={};evidence={}
for n in live:
    raw=(R/n).read_bytes();inputs['live:'+n]=meta(raw)
    if n in ('AGENTS.md','docs/11-RISK-REGISTER.md') or 'ADR-064' in n:
        evidence['live:'+n]=lines(raw,[(1,len(raw.decode('utf-8').splitlines()))])
    # Mandatory documents read in full without creating redundant source export.
    if n not in ('AGENTS.md','docs/11-RISK-REGISTER.md') and 'ADR-064' not in n:
        print('\nMANDATORY/CONTEXT',n,meta(raw));print(raw.decode('utf-8'))
prefix='src/main/java/io/github/sunthemoon/advancedrocketrycommunity/'
paths=['persistence/ChunkSaveDenials.java','persistence/GuardedChunkSaves.java','persistence/BoundedNbt.java',
       'machine/tank/TankProtection.java','machine/tank/TankSave.java','machine/tank/PressurizedTankBlockEntity.java',
       'machine/pump/PumpProtection.java','machine/pump/PumpSave.java','machine/pump/PumpBlockEntity.java',
       'machine/combustion/CombustionProtection.java','machine/combustion/CombustionSave.java',
       'machine/combustion/CombustionGeneratorBlockEntity.java','machine/recipe/RecipeSignatureProtection.java',
       'machine/recipe/RecipeSignatureMigration.java','gametest/GuardedChunkSaveGameTests.java',
       'gametest/RecipeSignatureSaveEventGameTests.java','machine/precision/PrecisionAssemblerManager.java']
selected={}
for short in paths:
    n=prefix+short;raw=git(['show',C+':'+n]);inputs[C+':'+n]=meta(raw)
    assert raw==(R/n).read_bytes(),n
    text=raw.decode('utf-8').splitlines()
    centers={i for i,l in enumerate(text,1) if any(t in l for t in ('bounded(', 'MAX_', 'boundedRoot(',
             'GuardedChunk','deny(', 'reason(', 'close(', 'restoreFixture(', 'saveAdditional(', 'void load(',
             'oversized =', 'refusedRoot =', 'refusedRoot !=', 'onChunkUnloaded(', 'setRemoved(',
             'flushMigrationBarrier(', 'getChunkSource().save', 'loadedRootsMatch', 'preservesRecipeInput')))}
    indices=set()
    for i in centers:indices.update(range(max(1,i-4),min(len(text),i+9)+1))
    if short in ('persistence/ChunkSaveDenials.java','persistence/GuardedChunkSaves.java',
                 'machine/recipe/RecipeSignatureProtection.java'):indices=set(range(1,len(text)+1))
    selected[n]=[{'line':i,'text':text[i-1]} for i in sorted(indices)]
    print('\nCODE',C,n,meta(raw));print('\n'.join(str(v['line'])+': '+v['text'] for v in selected[n]))
tests='src/test/java/io/github/sunthemoon/advancedrocketrycommunity/persistence/ChunkSaveDenialsTest.java'
raw=git(['show',C+':'+tests]);inputs[C+':'+tests]=meta(raw)
selected[tests]=lines(raw,[(1,100)])
queries={}
for key,args in [('guard-consumers',['grep','-n','GuardedChunkSaves\\|ChunkSaveDenials',C,'--','src/main','src/test']),
                 ('git-status',['status','--short','--untracked-files=no']),('git-worktrees',['worktree','list','--porcelain'])]:
    raw=git(args);queries[key]={'command':['git']+args,**meta(raw),'text':raw.decode('utf-8')}
    print('\nQUERY',key,'\n'+raw.decode('utf-8'))

assert meta(F.read_bytes())['sha256']=='918a11bdfceace2752d4c29bddbdf327981e1f6a1e1f0675f23e5fbf01e226c0'
assert meta(M.read_bytes())['sha256']=='95eecc5985233d83a6571299f89f02de034267646da171f7b36a5be2d394d71e'
inputs['primary:'+str(F)]=meta(F.read_bytes());inputs['primary:'+str(M)]=meta(M.read_bytes())
primary={}
primarynames=['patches/net/minecraft/server/level/ChunkMap.java.patch',
              'patches/net/minecraft/world/level/chunk/LevelChunk.java.patch',
              'patches/net/minecraft/world/level/chunk/storage/ChunkSerializer.java.patch',
              'net/minecraftforge/event/level/ChunkDataEvent.java','net/minecraftforge/event/level/ChunkEvent.java',
              'net/minecraftforge/common/capabilities/CapabilityDispatcher.java',
              'net/minecraftforge/common/capabilities/CapabilityProvider.java',
              'net/minecraftforge/common/ForgeInternalHandler.java']
with zipfile.ZipFile(F) as z:
    for n in primarynames:
        raw=z.read(n);inputs['primary:'+str(F)+'!/'+n]=meta(raw)
        primary[n]=lines(raw,[(1,len(raw.decode('utf-8').splitlines()))])
        print('\nPRIMARY',n,meta(raw));print(raw.decode('utf-8'))
# Prior disassembly already executed by a different historical task, no javap launched here.
incident=R/'docs/work/v1.8.0-c16a-save-guard/reviews/native-incident-01-evidence.zip'
inputs['archive:'+str(incident)]=meta(incident.read_bytes())
disassembly={}
with zipfile.ZipFile(incident) as z:
    assert z.testzip() is None
    manifest=json.loads(z.read('EVIDENCE-MANIFEST.json'))['files']
    for n,regions in {'bytecode/patched-runtime-ChunkMap.txt':[(2103,2240),(5339,5432),(1174,1217),(1331,1437)],
                      'bytecode/patched-runtime-LevelChunk.txt':[(1914,1949)],
                      'bytecode/patched-runtime-ServerLevel.txt':[(2207,2268),(2685,2696)],
                      'bytecode/patched-runtime-MinecraftServer.txt':[(1480,1624)],
                      'bytecode/mapped-ChunkSerializer.txt':[(850,890),(1099,1151),(1200,1278)],
                      'bytecode/mapped-ChunkMap.txt':[(2019,2101),(1174,1238)]}.items():
        raw=z.read(n);assert meta(raw)==manifest[n]
        inputs['archive-member:'+str(incident)+'!/'+n]=meta(raw)
        disassembly[n]=lines(raw,regions)
        print('\nPINNED HISTORICAL DISASSEMBLY',n,meta(raw))
        print('\n'.join(str(v['line'])+': '+v['text'] for v in disassembly[n]))
result={'observed_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'immutable_code_commit':C,
        'inputs':inputs,'live_doc_evidence':evidence,'selected_committed_code_lines':selected,
        'primary_source_lines':primary,'historical_primary_disassembly_lines':disassembly,'queries':queries,
        'no_fullsource_or_archive_copy':True,'Java_native_launches':0}
(B/'INPUT-EVIDENCE-01.json').write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
print('Named immutable code / live governance / exact primary inspection complete')
