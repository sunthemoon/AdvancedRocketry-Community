"""Use actual manifest-named recipe GT/logger classes; preserve failed04 lookup."""
from pathlib import Path
p=Path(__file__).resolve().with_name('gt_context04.py')
source=p.read_text(encoding='utf-8')
for before,after in [('RecipeSignatureGameTests.java','RecipeSignatureMigrationGameTests.java'),
                     ('MachineRecipeRepository.java','DeferredProcessDefinition.java')]:
    assert source.count(before)==1
    source=source.replace(before,after)
exec(compile(source,str(p.with_name('gt_context05.py')),'exec'))
