package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern;

import java.util.Objects;

/** Closed matcher set; arbitrary callbacks, scripts and recursive optionals are excluded. */
public sealed interface PatternMatcher {
    boolean matches(PatternBlock block);

    String summary();

    record ExactBlock(String blockId) implements PatternMatcher {
        public ExactBlock {
            blockId = PatternIds.requireResourceId("blockId", blockId);
        }

        @Override
        public boolean matches(PatternBlock block) {
            return block.blockId().equals(blockId);
        }

        @Override
        public String summary() {
            return "block=" + blockId;
        }
    }

    record BlockTag(String tagId) implements PatternMatcher {
        public BlockTag {
            tagId = PatternIds.requireResourceId("tagId", tagId);
        }

        @Override
        public boolean matches(PatternBlock block) {
            return block.tags().contains(tagId);
        }

        @Override
        public String summary() {
            return "tag=" + tagId;
        }
    }

    record Air() implements PatternMatcher {
        @Override
        public boolean matches(PatternBlock block) {
            return block.air();
        }

        @Override
        public String summary() {
            return "air";
        }
    }

    record Controller() implements PatternMatcher {
        @Override
        public boolean matches(PatternBlock block) {
            return block.controller();
        }

        @Override
        public String summary() {
            return "controller";
        }
    }

    record Port(String channel) implements PatternMatcher {
        public Port {
            channel = PatternIds.requireChannel("channel", channel);
        }

        @Override
        public boolean matches(PatternBlock block) {
            return block.portChannel().filter(channel::equals).isPresent();
        }

        @Override
        public String summary() {
            return "port=" + channel;
        }
    }

    record OptionalCell(PatternMatcher matcher) implements PatternMatcher {
        public OptionalCell {
            Objects.requireNonNull(matcher, "matcher");
            if (matcher instanceof OptionalCell) {
                throw new IllegalArgumentException("optional matchers cannot be recursive");
            }
        }

        @Override
        public boolean matches(PatternBlock block) {
            return block.air() || matcher.matches(block);
        }

        @Override
        public String summary() {
            return "optional(" + matcher.summary() + ")";
        }
    }
}
