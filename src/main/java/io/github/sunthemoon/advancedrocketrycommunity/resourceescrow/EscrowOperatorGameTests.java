package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSource;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscrowOperatorGameTests {
    private EscrowOperatorGameTests() { }
    @GameTest(template = "empty")
    public static void permissionTwoDiagnosticsAreBoundedReadOnlyAndNeverLoadChunks(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer(); var capture = new Output();
        var source = server.createCommandSourceStack().withSource(capture).withPermission(2);
        var dispatcher = server.getCommands().getDispatcher();
        var data = ResourceEscrowSavedData.existing(server);
        var before = data == null ? null : EscrowCodec.encode(data.ledger());
        int chunks = helper.getLevel().getChunkSource().getLoadedChunksCount();
        for (String command : List.of("status", "inspect " + UUID.randomUUID(), "retry " + UUID.randomUUID())) {
            capture.lines.clear(); dispatcher.execute("arce escrow " + command, source);
            helper.assertTrue(!capture.lines.isEmpty() && capture.lines.size() <= 5
                    && capture.lines.stream().allMatch(line -> line.length() < 1200), "Bounded command response");
            var denied = dispatcher.parse("arce escrow " + command, source.withPermission(1));
            helper.assertTrue(denied.getReader().canRead(), "Permission one must not parse escrow commands");
        }
        var after = ResourceEscrowSavedData.existing(server);
        helper.assertTrue(before == null ? after == null : after != null && before.equals(EscrowCodec.encode(after.ledger())),
                "Diagnostics must not create or mutate a ledger");
        helper.assertTrue(chunks == helper.getLevel().getChunkSource().getLoadedChunksCount(), "Diagnostics never load chunks");
        helper.succeed();
    }
    static final class Output implements CommandSource {
        final List<String> lines = new ArrayList<>();
        @Override public void sendSystemMessage(Component message) { lines.add(message.getString()); }
        @Override public boolean acceptsSuccess() { return true; }
        @Override public boolean acceptsFailure() { return true; }
        @Override public boolean shouldInformAdmins() { return false; }
    }
}
