"""Disposable client directories and official-runtime command construction."""
from .cells import check_game, copy_tree


def prepare_client(cell, spec, game, port, java, singleplayer=False):
    # Only game data is copied. Never accept an owner's launcher or account directory.
    source = cell["client_game_dir"]
    game.mkdir()
    for name in ("mods", "config", "defaultconfigs", "resourcepacks", "saves"):
        if (source / name).exists():
            copy_tree(source / name, game / name)
    (game / "options.txt").write_text(
        f"lang:{spec['language']}\nguiScale:{spec['gui_scale']}\nfullscreen:false\n"
        "pauseOnLostFocus:false\ntutorialStep:none\nonboardAccessibility:false\n"
        "skipMultiplayerWarning:true\njoinedFirstServer:true\nnarrator:0\n", encoding="utf-8")
    check_game(cell, game)
    # This helper reads only the isolated official runtime, never a signed-in launcher profile.
    from scripts.v100_compatibility_runtime import client_command
    command = client_command(cell["runtime_root"], game, cell["forge"], spec["player_name"], port, str(java))
    for flag, value in (("--width", spec["window_size"][0]), ("--height", spec["window_size"][1])):
        command[command.index(flag) + 1] = str(value)
    if singleplayer:
        index = command.index("--quickPlayMultiplayer")
        del command[index:index + 2]
    return command
