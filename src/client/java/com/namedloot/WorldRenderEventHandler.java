package com.namedloot;

import com.namedloot.config.NamedLootConfig;
import com.namedloot.util.TickDeltaResolver;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Rarity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;

import java.util.*;

public class WorldRenderEventHandler {

    public static void registerEvents() {
        WorldRenderEvents.LAST.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.isPaused() || client.world == null) {
                return;
            }

            List<ItemEntity> itemEntitiesToRender = new ArrayList<>();
            for (ItemEntity entity : client.world.getEntitiesByClass(
                    ItemEntity.class,
                    new Box(client.gameRenderer.getCamera().getBlockPos()).expand(
                            NamedLootClient.CONFIG.displayDistance > 0 ?
                                    NamedLootClient.CONFIG.displayDistance : 64),
                    itemEntity -> true)) {

                if (NamedLootClient.CONFIG.displayDistance > 0) {
                    double distance = client.gameRenderer.getCamera().getPos().distanceTo(entity.getPos());
                    if (distance > NamedLootClient.CONFIG.displayDistance) continue;
                }

                if (NamedLootClient.CONFIG.showNameOnHover) {
                    if (isPlayerLookingAt(client, entity)) itemEntitiesToRender.add(entity);
                } else {
                    itemEntitiesToRender.add(entity);
                }
            }

            if (itemEntitiesToRender.isEmpty()) return;

            MatrixStack matrices = context.matrixStack();

            // Tick delta extraction
            float tickDelta = TickDeltaResolver.get(client);

            TextRenderer textRenderer = client.textRenderer;
            VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();

            Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
            itemEntitiesToRender.sort(Comparator.comparingDouble(
                    entity -> -entity.getPos().distanceTo(cameraPos)
            ));

            for (ItemEntity entity : itemEntitiesToRender) {
                renderItemNameTag(entity, matrices, immediate, client, textRenderer, tickDelta);
            }
        });
    }

    private static boolean isPlayerLookingAt(MinecraftClient client, ItemEntity entity) {
        Vec3d entityPos = entity.getPos();
        Box entityBox = entity.getBoundingBox();

        assert client.player != null;
        Vec3d lookVec = client.player.getRotationVec(1.0F);
        Vec3d playerPos = client.player.getEyePos();

        double reachDistance = (NamedLootClient.CONFIG.displayDistance > 0)
                ? NamedLootClient.CONFIG.displayDistance
                : 32.0;

        Vec3d endPos = playerPos.add(lookVec.multiply(reachDistance));
        var hitResult = entityBox.expand(0.5).raycast(playerPos, endPos);
        if (hitResult.isEmpty()) return false;

        assert client.world != null;
        var blockHitResult = client.world.raycast(
                new RaycastContext(
                        playerPos,
                        entityPos,
                        RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE,
                        client.player
                )
        );

        if (blockHitResult.getType() != HitResult.Type.MISS) {
            double blockDist = blockHitResult.getPos().distanceTo(playerPos);
            double entityDist = entityPos.distanceTo(playerPos);
            return !(blockDist < entityDist);
        }
        return true;
    }

    private static void renderItemNameTag(ItemEntity entity,
                                          MatrixStack matrices,
                                          VertexConsumerProvider vertexConsumers,
                                          MinecraftClient client,
                                          TextRenderer textRenderer,
                                          float tickDelta) {
        if (matrices == null) return;

        MutableText formattedText = null;
        ItemStack stack = entity.getStack();
        String name = stack.getName().getString();
        int count = stack.getCount();
        List<NamedLootConfig.AdvancedRule> rules = NamedLootClient.CONFIG.advancedRules;

        boolean advancedRuleApplied = false;

        int i = 0;
        while (i < rules.size()) {
            NamedLootConfig.AdvancedRule leader = rules.get(i);
            if (!leader.ruleEnabled) {
                int nextGroupStartIndex = i + 1;
                while (nextGroupStartIndex < rules.size() &&
                        (rules.get(nextGroupStartIndex).textFormat == null ||
                                rules.get(nextGroupStartIndex).textFormat.isEmpty())) {
                    nextGroupStartIndex++;
                }
                i = nextGroupStartIndex;
                continue;
            }

            List<NamedLootConfig.AdvancedRule> groupConditions = new ArrayList<>();
            groupConditions.add(leader);
            int nextIndex = i + 1;
            while (nextIndex < rules.size() &&
                    (rules.get(nextIndex).textFormat == null ||
                            rules.get(nextIndex).textFormat.isEmpty())) {
                groupConditions.add(rules.get(nextIndex));
                nextIndex++;
            }

            boolean allConditionsMet = true;
            for (NamedLootConfig.AdvancedRule condition : groupConditions) {
                if (!checkCondition(condition, name, count)) {
                    allConditionsMet = false;
                    break;
                }
            }

            if (allConditionsMet) {
                formattedText = parseFormattedText(leader.textFormat, stack, String.valueOf(count));
                advancedRuleApplied = true;
                break;
            }

            i = nextIndex;
        }

        if (!advancedRuleApplied) {
            if (!NamedLootClient.CONFIG.enabled) return;
            formattedText = NamedLootClient.CONFIG.useManualFormatting
                    ? parseFormattedText(NamedLootClient.CONFIG.textFormat, stack, String.valueOf(count))
                    : createAutomaticFormattedText(stack, String.valueOf(count));
        }

        matrices.push();

        Vec3d interpolatedPos = entity.getLerpedPos(tickDelta);
        Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
        matrices.translate(
                interpolatedPos.x - cameraPos.x,
                interpolatedPos.y - cameraPos.y + entity.getHeight() + NamedLootClient.CONFIG.verticalOffset,
                interpolatedPos.z - cameraPos.z
        );

        float cameraYaw = client.gameRenderer.getCamera().getYaw();
        float cameraPitch = client.gameRenderer.getCamera().getPitch();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-cameraYaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cameraPitch));

        matrices.scale(-0.025F, -0.025F, -0.025F);

        float textOffset = -textRenderer.getWidth(formattedText) / 2.0F;

        boolean shouldShowDetails = advancedRuleApplied
                ? NamedLootClient.CONFIG.showDetails
                : (NamedLootClient.CONFIG.enabled && NamedLootClient.CONFIG.showDetails);

        if (shouldShowDetails && NamedLootClient.CONFIG.showDetailsOnlyOnHover) {
            shouldShowDetails = isPlayerLookingAt(client, entity);
        }

        List<Text> details = new ArrayList<>();
        if (shouldShowDetails) {
            var enchContainer = EnchantmentHelper.getEnchantments(entity.getStack());
            Set<RegistryEntry<Enchantment>> enchEntries = enchContainer.getEnchantments();
            for (RegistryEntry<Enchantment> e : enchEntries) {
                int level = enchContainer.getLevel(e);
                Text enchName = Enchantment.getName(e, level);
                details.add(enchName);
            }
        }

        textRenderer.draw(
                formattedText,
                textOffset,
                shouldShowDetails && !details.isEmpty() ? -(details.size() * 10) - 10 : 0,
                0xFFFFFFFF,
                false,
                matrices.peek().getPositionMatrix(),
                vertexConsumers,
                NamedLootClient.CONFIG.useSeeThrough
                        ? TextRenderer.TextLayerType.SEE_THROUGH
                        : TextRenderer.TextLayerType.NORMAL,
                NamedLootClient.CONFIG.useBackgroundColor
                        ? NamedLootClient.CONFIG.backgroundColor : 0x00000000,
                0xF000F0
        );

        if (shouldShowDetails && !details.isEmpty()) {
            if (NamedLootClient.CONFIG.useBackgroundColor && NamedLootClient.CONFIG.useDetailBackgroundBox) {
                int lineHeight = 10;
                int padding = 2;
                int maxWidth = details.stream().mapToInt(textRenderer::getWidth).max().orElse(0);
                float xOffset = textOffset - padding;
                float yOffset = -(details.size() * lineHeight);
                float width = xOffset + maxWidth + padding * 2;
                float height = yOffset + (details.size() * lineHeight) + padding;
                drawBackgroundBox(matrices, vertexConsumers, xOffset, yOffset, width, height,
                        NamedLootClient.CONFIG.detailBackgroundColor,
                        NamedLootClient.CONFIG.useSeeThrough);
            }
        }

        if (shouldShowDetails && !details.isEmpty()) {
            float yOffset = 0;
            int detailColor = 0xFFAAAAAA;
            for (Text detail : details) {
                textRenderer.draw(
                        detail,
                        textOffset,
                        -(details.size() * 10) + 2 + yOffset,
                        detailColor,
                        false,
                        matrices.peek().getPositionMatrix(),
                        vertexConsumers,
                        NamedLootClient.CONFIG.useSeeThrough
                                ? TextRenderer.TextLayerType.SEE_THROUGH
                                : TextRenderer.TextLayerType.NORMAL,
                        (NamedLootClient.CONFIG.useBackgroundColor && !NamedLootClient.CONFIG.useDetailBackgroundBox)
                                ? NamedLootClient.CONFIG.detailBackgroundColor : 0x00000000,
                        0xF000F0
                );
                yOffset += 10;
            }
        }

        matrices.pop();
    }

    private static boolean checkCondition(NamedLootConfig.AdvancedRule rule, String name, int count) {
        if (rule.value == null || rule.value.isEmpty()) return false;
        switch (rule.condition) {
            case "Contains":
                return name.toLowerCase().contains(rule.value.toLowerCase());
            case "Count <":
                try { return count < Integer.parseInt(rule.value); } catch (NumberFormatException e) { return false; }
            case "Count >":
                try { return count > Integer.parseInt(rule.value); } catch (NumberFormatException e) { return false; }
            case "Count =":
                try { return count == Integer.parseInt(rule.value); } catch (NumberFormatException e) { return false; }
        }
        return false;
    }

    private static void drawBackgroundBox(MatrixStack matrices,
                                          VertexConsumerProvider provider,
                                          float x1, float y1, float x2, float y2,
                                          int color,
                                          boolean useSeeThrough) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        RenderLayer layer = useSeeThrough ? RenderLayer.getTextBackgroundSeeThrough() : RenderLayer.getTextBackground();
        VertexConsumer buffer = provider.getBuffer(layer);

        float red   = (float)(color >> 16 & 255) / 255.0F;
        float green = (float)(color >> 8  & 255) / 255.0F;
        float blue  = (float)(color       & 255) / 255.0F;
        float alpha = (float)(color >> 24 & 255) / 255.0F;

        buffer.vertex(matrix, x1, y2, -1).color(red, green, blue, alpha).light(0xF000F0);
        buffer.vertex(matrix, x2, y2, -1).color(red, green, blue, alpha).light(0xF000F0);
        buffer.vertex(matrix, x2, y1, -1).color(red, green, blue, alpha).light(0xF000F0);
        buffer.vertex(matrix, x1, y1, -1).color(red, green, blue, alpha).light(0xF000F0);
    }

    public static MutableText createAutomaticFormattedText(ItemStack itemStack, String countText) {
        MutableText formattedText = Text.literal("");
        String format = NamedLootClient.CONFIG.textFormat;

        int configNameRed = (int)(NamedLootClient.CONFIG.nameRed * 255);
        int configNameGreen = (int)(NamedLootClient.CONFIG.nameGreen * 255);
        int configNameBlue = (int)(NamedLootClient.CONFIG.nameBlue * 255);
        int configNameColor = (configNameRed << 16) | (configNameGreen << 8) | configNameBlue;

        int countRed = (int)(NamedLootClient.CONFIG.countRed * 255);
        int countGreen = (int)(NamedLootClient.CONFIG.countGreen * 255);
        int countBlue = (int)(NamedLootClient.CONFIG.countBlue * 255);
        int countColor = (countRed << 16) | (countGreen << 8) | countBlue;

        int currentIndex = 0;
        while (currentIndex < format.length()) {
            int nameIndex = format.indexOf("{name}", currentIndex);
            int countIndex = format.indexOf("{count}", currentIndex);

            int nextPlaceholderIndex = -1;
            String placeholderType = null;

            if (nameIndex != -1 && (countIndex == -1 || nameIndex < countIndex)) {
                nextPlaceholderIndex = nameIndex;
                placeholderType = "name";
            } else if (countIndex != -1) {
                nextPlaceholderIndex = countIndex;
                placeholderType = "count";
            }

            if (nextPlaceholderIndex == -1) {
                formattedText.append(Text.literal(format.substring(currentIndex)));
                break;
            }

            if (nextPlaceholderIndex > currentIndex) {
                formattedText.append(Text.literal(format.substring(currentIndex, nextPlaceholderIndex)));
            }

            if ("name".equals(placeholderType)) {
                if (!NamedLootClient.CONFIG.overrideItemColors) {
                    TextColor existingColor = itemStack.getName().getStyle().getColor();
                    boolean isCommon = itemStack.getRarity().equals(Rarity.COMMON);
                    if ((existingColor != null && existingColor != TextColor.fromFormatting(Formatting.WHITE)) || !isCommon) {
                        formattedText.append(itemStack.getFormattedName());
                    } else {
                        String plainName = itemStack.getName().getString();
                        Style nameStyle = Style.EMPTY.withColor(configNameColor);
                        if (NamedLootClient.CONFIG.nameBold) nameStyle = nameStyle.withBold(true);
                        if (NamedLootClient.CONFIG.nameItalic) nameStyle = nameStyle.withItalic(true);
                        if (NamedLootClient.CONFIG.nameUnderline) nameStyle = nameStyle.withUnderline(true);
                        if (NamedLootClient.CONFIG.nameStrikethrough) nameStyle = nameStyle.withStrikethrough(true);
                        formattedText.append(Text.literal(plainName).setStyle(nameStyle));
                    }
                } else {
                    String plainName = itemStack.getName().getString();
                    Style nameStyle = Style.EMPTY.withColor(configNameColor);
                    if (NamedLootClient.CONFIG.nameBold) nameStyle = nameStyle.withBold(true);
                    if (NamedLootClient.CONFIG.nameItalic) nameStyle = nameStyle.withItalic(true);
                    if (NamedLootClient.CONFIG.nameUnderline) nameStyle = nameStyle.withUnderline(true);
                    if (NamedLootClient.CONFIG.nameStrikethrough) nameStyle = nameStyle.withStrikethrough(true);
                    formattedText.append(Text.literal(plainName).setStyle(nameStyle));
                }
                currentIndex = nextPlaceholderIndex + "{name}".length();
            } else {
                Style countStyle = Style.EMPTY.withColor(countColor);
                if (NamedLootClient.CONFIG.countBold) countStyle = countStyle.withBold(true);
                if (NamedLootClient.CONFIG.countItalic) countStyle = countStyle.withItalic(true);
                if (NamedLootClient.CONFIG.countUnderline) countStyle = countStyle.withUnderline(true);
                if (NamedLootClient.CONFIG.countStrikethrough) countStyle = countStyle.withStrikethrough(true);
                formattedText.append(Text.literal(countText).setStyle(countStyle));
                currentIndex = nextPlaceholderIndex + "{count}".length();
            }
        }
        return formattedText;
    }

    public static MutableText parseFormattedText(String format, ItemStack itemStack, String countText) {
        MutableText result = Text.literal("");
        Style currentStyle = Style.EMPTY;
        StringBuilder currentSegment = new StringBuilder();

        for (int idx = 0; idx < format.length(); idx++) {
            char c = format.charAt(idx);

            if (c == '&' && idx + 1 < format.length()) {
                if (!currentSegment.isEmpty()) {
                    result.append(Text.literal(currentSegment.toString()).setStyle(currentStyle));
                    currentSegment.setLength(0);
                }
                currentStyle = applyFormatCode(currentStyle, format.charAt(++idx));
                continue;
            }

            if (c == '{') {
                int end = format.indexOf('}', idx);
                if (end != -1) {
                    String ph = format.substring(idx, end + 1);
                    if (ph.equals("{name}")) {
                        result.append(Text.literal(currentSegment.toString()).setStyle(currentStyle));
                        currentSegment.setLength(0);

                        MutableText nameText;
                        if (!NamedLootClient.CONFIG.overrideItemColors &&
                                (itemStack.getName().getStyle().getColor() != null ||
                                        !itemStack.getRarity().equals(Rarity.COMMON))) {
                            nameText = itemStack.getFormattedName().copy();
                        } else {
                            nameText = Text.literal(itemStack.getName().getString()).setStyle(currentStyle);
                        }
                        result.append(nameText);
                        idx = end;
                        continue;
                    } else if (ph.equals("{count}")) {
                        result.append(Text.literal(currentSegment.toString()).setStyle(currentStyle));
                        currentSegment.setLength(0);
                        result.append(Text.literal(countText).setStyle(currentStyle));
                        idx = end;
                        continue;
                    }
                }
            }

            currentSegment.append(c);
        }

        if (!currentSegment.isEmpty()) {
            result.append(Text.literal(currentSegment.toString()).setStyle(currentStyle));
        }

        return result;
    }

    private static Style applyFormatCode(Style currentStyle, char code) {
        return switch (code) {
            case '0' -> currentStyle.withColor(TextColor.fromRgb(0x000000));
            case '1' -> currentStyle.withColor(TextColor.fromRgb(0x0000AA));
            case '2' -> currentStyle.withColor(TextColor.fromRgb(0x00AA00));
            case '3' -> currentStyle.withColor(TextColor.fromRgb(0x00AAAA));
            case '4' -> currentStyle.withColor(TextColor.fromRgb(0xAA0000));
            case '5' -> currentStyle.withColor(TextColor.fromRgb(0xAA00AA));
            case '6' -> currentStyle.withColor(TextColor.fromRgb(0xFFAA00));
            case '7' -> currentStyle.withColor(TextColor.fromRgb(0xAAAAAA));
            case '8' -> currentStyle.withColor(TextColor.fromRgb(0x555555));
            case '9' -> currentStyle.withColor(TextColor.fromRgb(0x5555FF));
            case 'a' -> currentStyle.withColor(TextColor.fromRgb(0x55FF55));
            case 'b' -> currentStyle.withColor(TextColor.fromRgb(0x55FFFF));
            case 'c' -> currentStyle.withColor(TextColor.fromRgb(0xFF5555));
            case 'd' -> currentStyle.withColor(TextColor.fromRgb(0xFF55FF));
            case 'e' -> currentStyle.withColor(TextColor.fromRgb(0xFFFF55));
            case 'f' -> currentStyle.withColor(TextColor.fromRgb(0xFFFFFF));
            case 'k' -> currentStyle.withObfuscated(true);
            case 'l' -> currentStyle.withBold(true);
            case 'm' -> currentStyle.withStrikethrough(true);
            case 'n' -> currentStyle.withUnderline(true);
            case 'o' -> currentStyle.withItalic(true);
            case 'r' -> Style.EMPTY;
            default -> currentStyle;
        };
    }
}