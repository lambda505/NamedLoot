package com.namedloot.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.item.ItemStack;
import com.namedloot.NamedLootClient;
import com.namedloot.WorldRenderEventHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;

import java.util.function.Consumer;

public class NamedLootModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return NamedLootConfigScreen::new;
    }

    public static class NamedLootConfigScreen extends Screen {
        private final Screen parent;
        private TextFieldWidget formatField;

        private int previousHeight;

        // Constants for color preview dimensions
        private static final int PREVIEW_SIZE = 30;
        private static final int PREVIEW_X_OFFSET = 120;

        // Add scrolling variables
        private int scrollOffset = 0;
        private boolean isScrolling = false;
        private int contentHeight = 450; // Approximate height of all content

        private int nameColorLabelYPos;
        private int countColorLabelYPos;
        private int textFormatLabelYPos;
        private int formatDescriptionYPos;

        private static final int SECTION_TITLE_COLOR = 0xFFFFAA00;
        private static final int SECTION_SEPARATOR_COLOR = 0x66FFFFFF;

        private boolean needsInlineColorReference = false;

        // private List<CheckboxWidget> checkboxes = new ArrayList<>();

        private int currentTab = 0; // 0: Default, 1: Advanced

        public NamedLootConfigScreen(Screen parent) {
            super(Text.translatable("text.namedloot.config"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            // Increased space below tabs

            if (previousHeight != 0 && previousHeight != this.height) {
                validateScrollOffset();
            }
            previousHeight = this.height;

            this.clearChildren();

            // Store tab buttons separately - don't add them as drawableChild yet
            // We'll render them manually in render()

            if (currentTab == 0) {
                renderDefaultTab();
            } else {
                renderAdvancedTab();
            }

            validateScrollOffset();
        }

        private void renderDefaultTab() {
            int yPos = 80 + scrollOffset;

            // ==========================================================
            // GENERAL SECTION
            // ==========================================================
            drawSectionHeader(yPos, "options.namedloot.section.general");
            yPos += 20;

            // Global Enable/Disable Toggle
            addCheckbox(
                    "options.namedloot.mod_enabled",
                    NamedLootClient.CONFIG.enabled,
                    (checkbox) -> NamedLootClient.CONFIG.enabled = checkbox,
                    this.width / 2 - 100, yPos, 200,
                    "options.namedloot.tooltip.mod_enabled"
            );
            yPos += 24;

            // Vertical Offset Slider
            SliderWidget verticalOffsetSlider = new SliderWidget(this.width / 2 - 100, yPos, 200, 20,
                    Text.translatable("options.namedloot.vertical_offset", NamedLootClient.CONFIG.verticalOffset),
                    NamedLootClient.CONFIG.verticalOffset / 2.0F) {
                @Override
                protected void updateMessage() {
                    this.setMessage(Text.translatable("options.namedloot.vertical_offset",
                            String.format("%.2f", NamedLootClient.CONFIG.verticalOffset)));
                }

                @Override
                protected void applyValue() {
                    NamedLootClient.CONFIG.verticalOffset = (float) (this.value * 2.0F);
                    this.updateMessage();
                }
            };
            this.addDrawableChild(verticalOffsetSlider);

            // Reset vertical offset button
            this.addDrawableChild(ButtonWidget.builder(
                            Text.translatable("options.namedloot.reset"), button -> {
                                NamedLootClient.CONFIG.verticalOffset = 0.5F;
                                this.init();
                            }).dimensions(this.width / 2 + 105, yPos, 40, 20)
                    .tooltip(Tooltip.of(Text.translatable("options.namedloot.tooltip.reset_format")))
                    .build());
            yPos += 26;

            // Display Distance Slider
            SliderWidget distanceSlider = new SliderWidget(this.width / 2 - 100, yPos, 200, 20,
                    Text.translatable("options.namedloot.display_distance",
                            NamedLootClient.CONFIG.displayDistance == 0 ? "∞" :
                                    String.format("%.1f", NamedLootClient.CONFIG.displayDistance)),
                    NamedLootClient.CONFIG.displayDistance / 64.0F) {
                @Override
                protected void updateMessage() {
                    this.setMessage(Text.translatable("options.namedloot.display_distance",
                            NamedLootClient.CONFIG.displayDistance == 0 ? "∞" :
                                    String.format("%.1f", NamedLootClient.CONFIG.displayDistance)));
                }

                @Override
                protected void applyValue() {
                    NamedLootClient.CONFIG.displayDistance = (float) (this.value * 64.0F);
                    if (NamedLootClient.CONFIG.displayDistance < 0.5F) {
                        NamedLootClient.CONFIG.displayDistance = 0.0F;
                    }
                    this.updateMessage();
                }
            };
            this.addDrawableChild(distanceSlider);

            // Reset distance button
            this.addDrawableChild(ButtonWidget.builder(
                            Text.translatable("options.namedloot.reset"), button -> {
                                NamedLootClient.CONFIG.displayDistance = 0.0F;
                                this.init();
                            }).dimensions(this.width / 2 + 105, yPos, 40, 20)
                    .tooltip(Tooltip.of(Text.translatable("options.namedloot.tooltip.reset_format")))
                    .build());
            yPos += 30;

            // ==========================================================
            // DISPLAY OPTIONS SECTION
            // ==========================================================
            drawSectionHeader(yPos, "options.namedloot.section.display");
            yPos += 20;

            // Replace button toggles with checkboxes
            addCheckbox(
                    "options.namedloot.override_colors",
                    NamedLootClient.CONFIG.overrideItemColors,
                    (checkbox) -> NamedLootClient.CONFIG.overrideItemColors = checkbox,
                    this.width / 2 - 100, yPos, 200,
                    null
            );
            yPos += 24;

            addCheckbox(
                    "options.namedloot.show_details",
                    NamedLootClient.CONFIG.showDetails,
                    (checkbox) -> {
                        NamedLootClient.CONFIG.showDetails = checkbox;
                        this.init(); // Reinitialize to show/hide the sub-option
                    },
                    this.width / 2 - 100, yPos, 200,
                    null
            );
            yPos += 24;

            // Show details on hover sub-option (only shown if showDetails is enabled)
            if (NamedLootClient.CONFIG.showDetails) {
                addCheckbox(
                        "options.namedloot.show_details_on_hover",
                        NamedLootClient.CONFIG.showDetailsOnlyOnHover,
                        (checkbox) -> NamedLootClient.CONFIG.showDetailsOnlyOnHover = checkbox,
                        this.width / 2 - 80, yPos, 160,
                        null
                );
                yPos += 24;
            }

            addCheckbox(
                    "options.namedloot.show_name_on_hover",
                    NamedLootClient.CONFIG.showNameOnHover,
                    (checkbox) -> {
                        NamedLootClient.CONFIG.showNameOnHover = checkbox;
                        this.init(); // Refresh to show/hide sub-option
                    },
                    this.width / 2 - 100, yPos, 200,
                    null
            );
            yPos += 24;

            addCheckbox(
                    "options.namedloot.see_through",
                    NamedLootClient.CONFIG.useSeeThrough,
                    (checkbox) -> NamedLootClient.CONFIG.useSeeThrough = checkbox,
                    this.width / 2 - 100, yPos, 200,
                    null
            );
            yPos += 24;

            addCheckbox(
                    "options.namedloot.background_color",
                    NamedLootClient.CONFIG.useBackgroundColor,
                    (checkbox) -> {
                        NamedLootClient.CONFIG.useBackgroundColor = checkbox;
                        this.init();
                    },
                    this.width / 2 - 100, yPos, 200,
                    null
            );
            yPos += 24;

            // Background color slider (only shown if background color is enabled)
            if (NamedLootClient.CONFIG.useBackgroundColor) {
                // Item name background opacity slider
                int labelSliderBackgroundOpacityY = yPos;
                float opacity = ((NamedLootClient.CONFIG.backgroundColor >>> 24) & 0xFF) / 255.0F;
                this.addDrawable((context, mouseX, mouseY, delta) -> context.drawTextWithShadow(this.textRenderer,
                        Text.translatable("options.namedloot.item_background_opacity"),
                        this.width / 2 - 100, labelSliderBackgroundOpacityY, 0xFFFFFF));
                yPos += 16;

                SliderWidget bgOpacitySlider = new SliderWidget(this.width / 2 - 100, yPos, 200, 20,
                        Text.translatable("options.namedloot.background_opacity_value", (int)(opacity * 100)),
                        opacity) {
                    @Override
                    protected void updateMessage() {
                        this.setMessage(Text.translatable("options.namedloot.background_opacity_value",
                                (int)(this.value * 100)));
                    }

                    @Override
                    protected void applyValue() {
                        int alpha = (int)(this.value * 255) & 0xFF;
                        NamedLootClient.CONFIG.backgroundColor = (alpha << 24) |
                                (NamedLootClient.CONFIG.backgroundColor & 0x00FFFFFF);
                        this.updateMessage();
                    }
                };
                this.addDrawableChild(bgOpacitySlider);
                yPos += 26;

                // Detail background type options as radio-style buttons
                int detailBackgroundTypeYPos = yPos;
                this.addDrawable((context, mouseX, mouseY, delta) -> context.drawTextWithShadow(this.textRenderer,
                        Text.translatable("options.namedloot.detail_background_type"),
                        this.width / 2 - 100, detailBackgroundTypeYPos, 0xFFFFFF));
                yPos += 16;

                // Two buttons side by side that act like radio buttons
                ButtonWidget boxButton = ButtonWidget.builder(
                        Text.literal("Box"), button -> {
                            NamedLootClient.CONFIG.useDetailBackgroundBox = true;
                            this.init();
                        }).dimensions(this.width / 2 - 100, yPos, 95, 20).build();

                ButtonWidget inlineButton = ButtonWidget.builder(
                        Text.literal("Inline"), button -> {
                            NamedLootClient.CONFIG.useDetailBackgroundBox = false;
                            this.init();
                        }).dimensions(this.width / 2 + 5, yPos, 95, 20).build();

                // Visually highlight the selected option
                if (NamedLootClient.CONFIG.useDetailBackgroundBox) {
                    boxButton.active = false; // Disable the selected button
                } else {
                    inlineButton.active = false;
                }

                this.addDrawableChild(boxButton);
                this.addDrawableChild(inlineButton);
                yPos += 26;

                // Detail background opacity slider
                int labelSliderDetailBackgroundOpacityY = yPos;
                float detailOpacity = ((NamedLootClient.CONFIG.detailBackgroundColor >>> 24) & 0xFF) / 255.0F;
                this.addDrawable((context, mouseX, mouseY, delta) -> context.drawTextWithShadow(this.textRenderer,
                        Text.translatable("options.namedloot.detail_background_opacity"),
                        this.width / 2 - 100, labelSliderDetailBackgroundOpacityY, 0xFFFFFF));
                yPos += 16;

                SliderWidget detailBgOpacitySlider = new SliderWidget(this.width / 2 - 100, yPos, 200, 20,
                        Text.translatable("options.namedloot.background_opacity_value", (int)(detailOpacity * 100)),
                        detailOpacity) {
                    @Override
                    protected void updateMessage() {
                        this.setMessage(Text.translatable("options.namedloot.background_opacity_value",
                                (int)(this.value * 100)));
                    }

                    @Override
                    protected void applyValue() {
                        int alpha = (int)(this.value * 255) & 0xFF;
                        NamedLootClient.CONFIG.detailBackgroundColor = (alpha << 24) |
                                (NamedLootClient.CONFIG.detailBackgroundColor & 0x00FFFFFF);
                        this.updateMessage();
                    }
                };
                this.addDrawableChild(detailBgOpacitySlider);
                yPos += 26;
            }

            // ==========================================================
            // TEXT FORMAT SECTION
            // ==========================================================
            yPos += 10;
            drawSectionHeader(yPos, "options.namedloot.section.formatting");
            yPos += 20;

            // Manual formatting toggle with a more descriptive label
            this.addDrawableChild(ButtonWidget.builder(
                    Text.translatable("options.namedloot.manual_formatting",
                            NamedLootClient.CONFIG.useManualFormatting ? "ON" : "OFF"), button -> {
                        // If manual formatting is active, save the manual value and restore the automatic value
                        if (NamedLootClient.CONFIG.useManualFormatting) {
                            NamedLootClient.CONFIG.manualTextFormat = NamedLootClient.CONFIG.textFormat;
                            NamedLootClient.CONFIG.textFormat = NamedLootClient.CONFIG.automaticTextFormat;
                        } else {
                            // If manual formatting is not active, save the automatic value and restore the manual value
                            NamedLootClient.CONFIG.automaticTextFormat = NamedLootClient.CONFIG.textFormat;
                            NamedLootClient.CONFIG.textFormat = NamedLootClient.CONFIG.manualTextFormat;
                        }
                        NamedLootClient.CONFIG.useManualFormatting = !NamedLootClient.CONFIG.useManualFormatting;
                        button.setMessage(Text.translatable("options.namedloot.manual_formatting",
                                NamedLootClient.CONFIG.useManualFormatting ? "ON" : "OFF"));
                        this.init();
                    }).dimensions(this.width / 2 - 100, yPos, 200, 20).build());
            yPos += 26;

            // Fix the text format label and field positioning
            this.textFormatLabelYPos = yPos;
            this.addDrawable((context, mouseX, mouseY, delta) -> context.drawTextWithShadow(this.textRenderer,
                    Text.translatable("options.namedloot.text_format"),
                    this.width / 2 - 100, textFormatLabelYPos, 0xFFFFFF));
            yPos += 15;

            // Format description if manual formatting is enabled
            this.formatDescriptionYPos = yPos;
            if (NamedLootClient.CONFIG.useManualFormatting) {
                this.addDrawable((context, mouseX, mouseY, delta) -> context.drawTextWithShadow(this.textRenderer,
                        Text.translatable("options.namedloot.format_description").formatted(Formatting.GRAY),
                        this.width / 2 - 100, formatDescriptionYPos, 0xFFFFFF));
            }

            yPos += 36;

            // Text Format Field
            formatField = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, yPos,
                    200, 20, Text.literal(""));
            formatField.setMaxLength(100);
            formatField.setText(NamedLootClient.CONFIG.textFormat);
            formatField.setChangedListener(text -> NamedLootClient.CONFIG.textFormat = text);
            this.addDrawableChild(formatField);

            // Reset format button
            this.addDrawableChild(ButtonWidget.builder(
                            Text.translatable("options.namedloot.reset"), button -> {
                                NamedLootClient.CONFIG.textFormat = "{name} x{count}";
                                formatField.setText("{name} x{count}");
                            }).dimensions(this.width / 2 + 105, yPos, 40, 20)
                    .tooltip(Tooltip.of(Text.translatable("options.namedloot.tooltip.reset_format")))
                    .build());
            yPos += 30;

            // If manual formatting is enabled, we show the color code reference
            if (NamedLootClient.CONFIG.useManualFormatting) {
                this.addDrawable((context, mouseX, mouseY, delta) -> {
                    // Draw color code reference
                    renderColorCodeReference(context);
                });

                yPos += 170; // Add space for all the color codes
            } else {
                // ==========================================================
                // NAME COLOR SECTION (only if not using manual formatting)
                // ==========================================================
                yPos += 15;

                this.nameColorLabelYPos = yPos;
                this.addDrawable((context, mouseX, mouseY, delta) -> context.drawTextWithShadow(this.textRenderer,
                        Text.translatable("options.namedloot.name_color"),
                        this.width / 2 - 100, nameColorLabelYPos, 0xFFFFFF));
                yPos += 16;

                // Name style options as checkboxes
                addCheckbox(
                        "options.namedloot.name_bold",
                        NamedLootClient.CONFIG.nameBold,
                        (checkbox) -> NamedLootClient.CONFIG.nameBold = checkbox,
                        this.width / 2 - 100, yPos, 95,
                        null
                );

                addCheckbox(
                        "options.namedloot.name_italic",
                        NamedLootClient.CONFIG.nameItalic,
                        (checkbox) -> NamedLootClient.CONFIG.nameItalic = checkbox,
                        this.width / 2 + 5, yPos, 95,
                        null
                );
                yPos += 24;

                addCheckbox(
                        "options.namedloot.name_underline",
                        NamedLootClient.CONFIG.nameUnderline,
                        (checkbox) -> NamedLootClient.CONFIG.nameUnderline = checkbox,
                        this.width / 2 - 100, yPos, 95,
                        null
                );

                addCheckbox(
                        "options.namedloot.name_strikethrough",
                        NamedLootClient.CONFIG.nameStrikethrough,
                        (checkbox) -> NamedLootClient.CONFIG.nameStrikethrough = checkbox,
                        this.width / 2 + 5, yPos, 95,
                        null
                );
                yPos += 26;

                // Name Color Sliders
                this.addNameColorSlider(yPos, "red", NamedLootClient.CONFIG.nameRed);
                yPos += 26;
                this.addNameColorSlider(yPos, "green", NamedLootClient.CONFIG.nameGreen);
                yPos += 26;
                this.addNameColorSlider(yPos, "blue", NamedLootClient.CONFIG.nameBlue);
                yPos += 26;

                // Reset name color button
                this.addDrawableChild(ButtonWidget.builder(
                                Text.translatable("options.namedloot.reset_colors"), button -> {
                                    NamedLootClient.CONFIG.nameRed = 1.0F;
                                    NamedLootClient.CONFIG.nameGreen = 1.0F;
                                    NamedLootClient.CONFIG.nameBlue = 1.0F;
                                    NamedLootClient.CONFIG.nameBold = false;
                                    NamedLootClient.CONFIG.nameItalic = false;
                                    NamedLootClient.CONFIG.nameUnderline = false;
                                    NamedLootClient.CONFIG.nameStrikethrough = false;
                                    this.init();
                                }).dimensions(this.width / 2 - 50, yPos, 100, 20)
                        .tooltip(Tooltip.of(Text.translatable("options.namedloot.tooltip.reset_format")))
                        .build());
                yPos += 30;

                // ==========================================================
                // COUNT COLOR SECTION (only if not using manual formatting)
                // ==========================================================

                this.countColorLabelYPos = yPos;
                this.addDrawable((context, mouseX, mouseY, delta) -> context.drawTextWithShadow(this.textRenderer,
                        Text.translatable("options.namedloot.count_color"),
                        this.width / 2 - 100, countColorLabelYPos, 0xFFFFFF));
                yPos += 16;

                // Count style options as checkboxes
                addCheckbox(
                        "options.namedloot.count_bold",
                        NamedLootClient.CONFIG.countBold,
                        (checkbox) -> NamedLootClient.CONFIG.countBold = checkbox,
                        this.width / 2 - 100, yPos, 95,
                        null
                );

                addCheckbox(
                        "options.namedloot.count_italic",
                        NamedLootClient.CONFIG.countItalic,
                        (checkbox) -> NamedLootClient.CONFIG.countItalic = checkbox,
                        this.width / 2 + 5, yPos, 95,
                        null
                );
                yPos += 24;

                addCheckbox(
                        "options.namedloot.count_underline",
                        NamedLootClient.CONFIG.countUnderline,
                        (checkbox) -> NamedLootClient.CONFIG.countUnderline = checkbox,
                        this.width / 2 - 100, yPos, 95,
                        null
                );

                addCheckbox(
                        "options.namedloot.count_strikethrough",
                        NamedLootClient.CONFIG.countStrikethrough,
                        (checkbox) -> NamedLootClient.CONFIG.countStrikethrough = checkbox,
                        this.width / 2 + 5, yPos, 95,
                        null
                );
                yPos += 26;

                // Count Color Sliders
                this.addCountColorSlider(yPos, "red", NamedLootClient.CONFIG.countRed);
                yPos += 26;
                this.addCountColorSlider(yPos, "green", NamedLootClient.CONFIG.countGreen);
                yPos += 26;
                this.addCountColorSlider(yPos, "blue", NamedLootClient.CONFIG.countBlue);
                yPos += 26;

                // Reset count color button
                this.addDrawableChild(ButtonWidget.builder(
                                Text.translatable("options.namedloot.reset_colors"), button -> {
                                    NamedLootClient.CONFIG.countRed = 1.0F;
                                    NamedLootClient.CONFIG.countGreen = 1.0F;
                                    NamedLootClient.CONFIG.countBlue = 1.0F;
                                    NamedLootClient.CONFIG.countBold = false;
                                    NamedLootClient.CONFIG.countItalic = false;
                                    NamedLootClient.CONFIG.countUnderline = false;
                                    NamedLootClient.CONFIG.countStrikethrough = false;
                                    this.init();
                                }).dimensions(this.width / 2 - 50, yPos, 100, 20)
                        .tooltip(Tooltip.of(Text.translatable("options.namedloot.tooltip.reset_format")))
                        .build());
                yPos += 30;
            }

            // Save and close button with clearer text
            this.addDrawableChild(ButtonWidget.builder(
                    Text.translatable("options.namedloot.save_and_close"), button -> {
                        // Save config and return to previous screen
                        NamedLootClient.saveConfig();
                        assert this.client != null;
                        this.client.setScreen(this.parent);
                    }).dimensions(this.width / 2 - 100, yPos, 200, 20).build());
            yPos += 20;

            // Set initial focus to text field
            this.setInitialFocus(formatField);

            // Calculate final content height based on actual rendered positions
            int finalYPos = yPos;
            this.contentHeight = finalYPos - (80 + scrollOffset);
        }

        private void renderAdvancedTab() {
            int yPos = 80 + scrollOffset;

            // ==========================================================
            // ADVANCED RULES SECTION
            // ==========================================================
            drawSectionHeader(yPos, "options.namedloot.section.advanced_rules");
            yPos += 20;

            // Add Rules Button (adds a new rule group)
            this.addDrawableChild(ButtonWidget.builder(
                            Text.translatable("options.namedloot.add_rules"), button -> {
                                NamedLootClient.CONFIG.advancedRules.add(new NamedLootConfig.AdvancedRule());
                                this.init();
                            }).dimensions(this.width / 2 - 50, yPos, 100, 20)
                    .tooltip(Tooltip.of(Text.translatable("options.namedloot.add_rules")))
                    .build());
            yPos += 30;


            if (NamedLootClient.CONFIG.advancedRules.isEmpty()) {
                final int noRulesY = yPos;
                this.addDrawable((context, mouseX, mouseY, delta) -> {
                    Text helpText = Text.translatable("options.namedloot.no_rules_help").formatted(Formatting.GRAY, Formatting.ITALIC);
                    context.drawCenteredTextWithShadow(this.textRenderer, helpText,
                            this.width / 2, noRulesY, 0xFFFFFF);
                });
                yPos += 40;
            } else {
                int ruleDisplayIndex = 1;
                int configRuleIndex = 0;
                while (configRuleIndex < NamedLootClient.CONFIG.advancedRules.size()) {
                    final int groupStartIndex = configRuleIndex;
                    NamedLootConfig.AdvancedRule firstRuleInGroup = NamedLootClient.CONFIG.advancedRules.get(groupStartIndex);

                    // --- Find all conditions that belong to this rule group ---
                    final List<NamedLootConfig.AdvancedRule> groupConditions = new ArrayList<>();
                    groupConditions.add(firstRuleInGroup);

                    int nextIndex = groupStartIndex + 1;
                    while (nextIndex < NamedLootClient.CONFIG.advancedRules.size()) {
                        NamedLootConfig.AdvancedRule nextRule = NamedLootClient.CONFIG.advancedRules.get(nextIndex);
                        // A chained condition is identified by a null or empty textFormat string.
                        if (nextRule.textFormat != null && !nextRule.textFormat.isEmpty()) {
                            break; // This is the start of a new rule group.
                        }
                        groupConditions.add(nextRule);
                        nextIndex++;
                    }

                    // --- Render Rule Header and Remove Button for the entire group ---
                    final int headerY = yPos;
                    int finalRuleDisplayIndex = ruleDisplayIndex;
                    this.addDrawable((context, mouseX, mouseY, delta) -> {
                        Text ruleTitle = Text.literal("Rule " + finalRuleDisplayIndex).formatted(Formatting.BOLD);
                        context.drawTextWithShadow(this.textRenderer, ruleTitle,
                                this.width / 2 - 100, headerY, SECTION_TITLE_COLOR);
                    });

                    this.addDrawableChild(ButtonWidget.builder(
                            Text.literal("−").formatted(Formatting.RED), btn -> {
                                NamedLootClient.CONFIG.advancedRules.removeAll(groupConditions);
                                this.init();
                            }).dimensions(this.width / 2 + 80, yPos, 20, 20).build());
                    yPos += 25;

                    // Enable/Disable Toggle for this specific rule group
                    addCheckbox(
                            "options.namedloot.rule_enabled",
                            firstRuleInGroup.ruleEnabled,
                            (checkbox) -> firstRuleInGroup.ruleEnabled = checkbox,
                            this.width / 2 - 100, yPos, 200,
                            "options.namedloot.tooltip.rule_enabled"
                    );
                    yPos += 24;

                    // --- Loop through and render each condition in the group ---
                    for (int i = 0; i < groupConditions.size(); i++) {
                        final int conditionIndexInConfig = groupStartIndex + i;
                        final NamedLootConfig.AdvancedRule conditionRule = groupConditions.get(i);

                        if (i > 0) {
                            final int andY = yPos;
                            this.addDrawable((context, mouseX, mouseY, delta) ->
                                    context.drawCenteredTextWithShadow(this.textRenderer,
                                            Text.literal("AND").formatted(Formatting.YELLOW, Formatting.BOLD),
                                            this.width / 2, andY, 0xFFFFFF));
                            yPos += 15;
                        }

                        final int conditionLabelY = yPos;
                        this.addDrawable((context, mouseX, mouseY, delta) ->
                                context.drawTextWithShadow(this.textRenderer,
                                        Text.translatable("options.namedloot.condition"),
                                        this.width / 2 - 100, conditionLabelY, 0xFFFFFF));
                        yPos += 16;

                        // Condition toggle buttons
                        int buttonWidth = 45;
                        int buttonSpacing = 5;
                        int startX = this.width / 2 - 100;

                        ButtonWidget containsButton = ButtonWidget.builder(
                                        Text.literal("Contains"), button -> {
                                            conditionRule.condition = "Contains";
                                            this.init();
                                        }).dimensions(startX, yPos, buttonWidth, 20)
                                .tooltip(Tooltip.of(Text.translatable("options.namedloot.tooltip.name_match")))
                                .build();

                        ButtonWidget countLessButton = ButtonWidget.builder(
                                        Text.literal("Count <"), button -> {
                                            conditionRule.condition = "Count <";
                                            this.init();
                                        }).dimensions(startX + (buttonWidth + buttonSpacing), yPos, buttonWidth, 20)
                                .tooltip(Tooltip.of(Text.translatable("options.namedloot.tooltip.count_less")))
                                .build();

                        ButtonWidget countMoreButton = ButtonWidget.builder(
                                        Text.literal("Count >"), button -> {
                                            conditionRule.condition = "Count >";
                                            this.init();
                                        }).dimensions(startX + (buttonWidth + buttonSpacing) * 2, yPos, buttonWidth, 20)
                                .tooltip(Tooltip.of(Text.translatable("options.namedloot.tooltip.count_more")))
                                .build();

                        ButtonWidget countEqualButton = ButtonWidget.builder(
                                        Text.literal("Count ="), button -> {
                                            conditionRule.condition = "Count =";
                                            this.init();
                                        }).dimensions(startX + (buttonWidth + buttonSpacing) * 3, yPos, buttonWidth, 20)
                                .tooltip(Tooltip.of(Text.translatable("options.namedloot.tooltip.count_equal")))
                                .build();

                        containsButton.active = !"Contains".equals(conditionRule.condition);
                        countLessButton.active = !"Count <".equals(conditionRule.condition);
                        countMoreButton.active = !"Count >".equals(conditionRule.condition);
                        countEqualButton.active = !"Count =".equals(conditionRule.condition);

                        this.addDrawableChild(containsButton);
                        this.addDrawableChild(countLessButton);
                        this.addDrawableChild(countMoreButton);
                        this.addDrawableChild(countEqualButton);
                        yPos += 26;

                        // Value label, text field, and the new remove condition button
                        final int valueLabelY = yPos;
                        this.addDrawable((context, mouseX, mouseY, delta) ->
                                context.drawTextWithShadow(this.textRenderer,
                                        Text.translatable("options.namedloot.rule_value"),
                                        this.width / 2 - 100, valueLabelY, 0xFFFFFF));
                        yPos += 16;

                        TextFieldWidget valueField = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, yPos, 180, 20, Text.literal(""));
                        valueField.setText(conditionRule.value);
                        valueField.setChangedListener(text -> conditionRule.value = text);
                        this.addDrawableChild(valueField);

                        // New remove condition ('-') button
                        this.addDrawableChild(ButtonWidget.builder(
                                        Text.literal("−").formatted(Formatting.RED), button -> {
                                            NamedLootClient.CONFIG.advancedRules.remove(conditionIndexInConfig);
                                            // If the first rule in a group is deleted, promote the next one to be the new "leader"
                                            if (conditionIndexInConfig == groupStartIndex && groupConditions.size() > 1) {
                                                NamedLootClient.CONFIG.advancedRules.get(groupStartIndex).textFormat = firstRuleInGroup.textFormat;
                                            }
                                            this.init();
                                        }).dimensions(this.width / 2 + 85, yPos, 20, 20)
                                .tooltip(Tooltip.of(Text.translatable("options.namedloot.tooltip.remove_condition")))
                                .build());
                        yPos += 26;
                    }

                    // --- New Add Condition ('+') Button ---
                    this.addDrawableChild(ButtonWidget.builder(
                                    Text.translatable("options.namedloot.add_condition").formatted(Formatting.GREEN), button -> {
                                        int insertAtIndex = groupStartIndex + groupConditions.size();
                                        NamedLootConfig.AdvancedRule newCondition = new NamedLootConfig.AdvancedRule();
                                        newCondition.textFormat = ""; // Empty format marks it as a chained condition
                                        NamedLootClient.CONFIG.advancedRules.add(insertAtIndex, newCondition);
                                        this.init();
                                    }).dimensions(this.width / 2 - 100, yPos, 205, 20)
                            .tooltip(Tooltip.of(Text.translatable("options.namedloot.tooltip.add_condition")))
                            .build());
                    yPos += 26;


                    // --- Shared Format Field for the Rule Group ---
                    final int formatLabelY = yPos;
                    this.addDrawable((context, mouseX, mouseY, delta) ->
                            context.drawTextWithShadow(this.textRenderer,
                                    Text.translatable("options.namedloot.rule_format"),
                                    this.width / 2 - 100, formatLabelY, 0xFFFFFF));
                    yPos += 16;

                    TextFieldWidget formatField = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, yPos, 200, 20, Text.literal(""));
                    formatField.setText(firstRuleInGroup.textFormat);
                    formatField.setChangedListener(text -> NamedLootClient.CONFIG.advancedRules.get(groupStartIndex).textFormat = text);
                    this.addDrawableChild(formatField);

                    this.addDrawableChild(ButtonWidget.builder(
                                    Text.translatable("options.namedloot.reset"), button -> {
                                        NamedLootClient.CONFIG.advancedRules.get(groupStartIndex).textFormat = "{name} x{count}";
                                        this.init();
                                    }).dimensions(this.width / 2 + 105, yPos, 40, 20)
                            .tooltip(Tooltip.of(Text.translatable("options.namedloot.tooltip.reset_format")))
                            .build());
                    yPos += 26;

                    // Rule separator line
                    if (configRuleIndex + groupConditions.size() < NamedLootClient.CONFIG.advancedRules.size()) {
                        final int separatorY = yPos;
                        this.addDrawable((context, mouseX, mouseY, delta) -> context.fill(this.width / 2 - 80, separatorY,
                                this.width / 2 + 80, separatorY + 1, 0x33FFFFFF));
                        yPos += 10;
                    }
                    yPos += 10;

                    // Update loop counters
                    configRuleIndex += groupConditions.size();
                    ruleDisplayIndex++;
                }
            }

            // Save and close button (consistent with default tab)
            this.addDrawableChild(ButtonWidget.builder(
                    Text.translatable("options.namedloot.save_and_close"), button -> {
                        NamedLootClient.saveConfig();
                        assert this.client != null;
                        this.client.setScreen(this.parent);
                    }).dimensions(this.width / 2 - 100, yPos, 200, 20).build());
            yPos += 20;

            int minSpacing = 20;
            int referenceWidth = 230;
            int mainContentWidth = 410;

            int availableLeftSpace = (this.width / 2 - mainContentWidth / 2) - minSpacing;
            boolean canFitLeft = availableLeftSpace >= referenceWidth;

            needsInlineColorReference = !(canFitLeft); //!(canFitLeft && referenceBottomY < saveButtonY);

            int extraTopPadding = 12;
            int finalYPos = yPos;
            int computedContentHeight;
            if (needsInlineColorReference) {
                // Reference ditempatkan inline dengan jarak ekstra
                int inlineY = yPos + extraTopPadding;
                this.addDrawable((context, mouseX, mouseY, delta) -> renderColorCodeContentAt(context, this.width / 2 - 100, this.width / 2 + 20, inlineY, false));
                //yPos += extraTopPadding + referenceBoxHeight;
                computedContentHeight = finalYPos - (80 + scrollOffset) + 190;
            }else{
                computedContentHeight = finalYPos - (80 + scrollOffset);
            }


            this.contentHeight = computedContentHeight; //Math.max(computedContentHeight, this.contentHeight);
        }


        // Helper method to draw a section header with a separator line
        private void drawSectionHeader(int yPos, String translationKey) {
            final int y = yPos;
            this.addDrawable((context, mouseX, mouseY, delta) -> {
                // Draw section title
                Text sectionTitle = Text.translatable(translationKey).formatted(Formatting.BOLD);
                context.drawTextWithShadow(this.textRenderer, sectionTitle,
                        this.width / 2 - 100, y, SECTION_TITLE_COLOR);

                // Draw separator line
                context.fill(this.width / 2 - 100, y + 12,
                        this.width / 2 + 100, y + 13, SECTION_SEPARATOR_COLOR);
            });
        }

        // Helper method for adding checkboxes with consistent styling
        private void addCheckbox(String translationKey, boolean configValue, Consumer<Boolean> configSetter,
                                 int x, int y, int width, @Nullable String tooltipKey) {
            MutableText label = Text.empty()
                    .append(Text.literal(configValue ? "☑ " : "☐ ").formatted(Formatting.GREEN))
                    .append(Text.translatable(translationKey));

            ButtonWidget checkbox = ButtonWidget.builder(label, button -> {
                        boolean newState = !configValue;
                        configSetter.accept(newState);
                        this.init(); // refresh
                    }).dimensions(x, y, width, 20)
                    .tooltip(tooltipKey != null ? Tooltip.of(Text.translatable(tooltipKey)) : null)
                    .build();

            this.addDrawableChild(checkbox);
        }

        private void addNameColorSlider(int y, String type, float initialValue) {
            SliderWidget slider = new SliderWidget(this.width / 2 - 100, y, 200, 20,
                    Text.translatable("options.namedloot.name_" + type, (int)(initialValue * 255)),
                    initialValue) {
                @Override
                protected void updateMessage() {
                    this.setMessage(Text.translatable("options.namedloot.name_" + type, (int)(this.value * 255)));
                }

                @Override
                protected void applyValue() {
                    switch (type) {
                        case "red" -> NamedLootClient.CONFIG.nameRed = (float) this.value;
                        case "green" -> NamedLootClient.CONFIG.nameGreen = (float) this.value;
                        case "blue" -> NamedLootClient.CONFIG.nameBlue = (float) this.value;
                    }
                    this.updateMessage();
                }
            };
            this.addDrawableChild(slider);
        }

        private void addCountColorSlider(int y, String type, float initialValue) {
            SliderWidget slider = new SliderWidget(this.width / 2 - 100, y, 200, 20,
                    Text.translatable("options.namedloot.count_" + type, (int)(initialValue * 255)),
                    initialValue) {
                @Override
                protected void updateMessage() {
                    this.setMessage(Text.translatable("options.namedloot.count_" + type, (int)(this.value * 255)));
                }

                @Override
                protected void applyValue() {
                    switch (type) {
                        case "red" -> NamedLootClient.CONFIG.countRed = (float) this.value;
                        case "green" -> NamedLootClient.CONFIG.countGreen = (float) this.value;
                        case "blue" -> NamedLootClient.CONFIG.countBlue = (float) this.value;
                    }
                    this.updateMessage();
                }
            };
            this.addDrawableChild(slider);
        }

        // Add a new method to ensure the scrollOffset is valid based on current dimensions
        private void validateScrollOffset() {
            final int contentYStart = 80;
            final int bottomMargin = 25;
            int visibleHeight = this.height - contentYStart - bottomMargin;

            if (scrollOffset > 0) {
                scrollOffset = 0;
            }

            if (contentHeight <= visibleHeight) {
                scrollOffset = 0;
            } else if (scrollOffset < -(contentHeight - visibleHeight)) {
                scrollOffset = -(contentHeight - visibleHeight);
            }
        }

        @Override
        public void resize(net.minecraft.client.MinecraftClient client, int width, int height) {
            super.resize(client, width, height);
        }


        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {

            // Do immediate scroll too for responsive feel
            scrollOffset = scrollOffset + (int)(verticalAmount * 20);
            // Validate the scroll position
            validateScrollOffset();
            // Reinitialize with the new scroll position
            this.init();
            return true;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            // Handle mouse dragging for scrolling
            if (isScrolling) {
                scrollOffset = scrollOffset - (int)deltaY;
                // Validate the new scroll position
                validateScrollOffset();
                // Reinitialize all elements with the new scroll position
                this.init();
                return true;
            }
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            // Handle tab button clicks first (before scissor area)
            if (mouseY >= 35 && mouseY <= 55) {
                if (mouseX >= (double) this.width / 2 - 100 && mouseX <= (double) this.width / 2 - 5) {
                    // Default tab clicked
                    currentTab = 0;
                    this.init();
                    return true;
                } else if (mouseX >= (double) this.width / 2 + 5 && mouseX <= (double) this.width / 2 + 100) {
                    // Advanced tab clicked
                    currentTab = 1;
                    this.init();
                    return true;
                }
            }

            // Handle scrollbar clicks
            if (button == 0 && mouseX > this.width - 15) {
                isScrolling = true;
                return true;
            }

            // Only handle other clicks if they're in the content area
            if (mouseY >= 80) {
                return super.mouseClicked(mouseX, mouseY, button);
            }

            return false;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            // Stop scrolling when mouse is released
            if (button == 0) { // Left mouse button
                isScrolling = false;
            }
            return super.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            // Render background
            //this.renderBackground(context, mouseX, mouseY, delta);

            // Draw title
            Text titleText = Text.literal("✦ ").formatted(Formatting.GOLD)
                    .append(this.title)
                    .append(Text.literal(" ✦").formatted(Formatting.GOLD));
            int titleX = this.width / 2;
            int titleY = 15;

            context.drawCenteredTextWithShadow(this.textRenderer, titleText, titleX, titleY, 0xFFFFFF);
            //context.fill(this.width / 4, titleY + 12, this.width * 3/4, titleY + 13, 0x55FFFFFF);

            // Render tab buttons manually (always visible, not affected by scroll)
            renderTabButtons(context, mouseX, mouseY, delta);

            // Create scissor area for scrollable content
            int clipStartY = 80;
            int clipHeight = this.height - clipStartY - 25;

            context.enableScissor(0, clipStartY, this.width, clipStartY + clipHeight);

            // Render all scrollable content
            super.render(context, mouseX, mouseY, delta);

            // Render color previews
            renderColorPreviews(context);

            // Render color code reference for Advanced tab (outside scissor)
            renderColorCodeReference(context);

            context.disableScissor();

            // Draw scrollbar outside scissor area
            if (contentHeight > clipHeight) {
                drawScrollbar(context);
            }
        }

        private void renderTabButtons(DrawContext context, int mouseX, int mouseY, float delta) {
            // Create tab buttons
            ButtonWidget defaultButton = ButtonWidget.builder(
                    Text.literal("Default"), button -> {
                        currentTab = 0;
                        this.init();
                    }).dimensions(this.width / 2 - 100, 35, 95, 20).build();

            ButtonWidget advancedButton = ButtonWidget.builder(
                    Text.literal("Advanced"), button -> {
                        currentTab = 1;
                        this.init();
                    }).dimensions(this.width / 2 + 5, 35, 95, 20).build();

            // Highlight active tab by making it inactive (darker)
            if (currentTab == 0) {
                defaultButton.active = false;
            } else {
                advancedButton.active = false;
            }

            // Render buttons
            defaultButton.render(context, mouseX, mouseY, delta);
            advancedButton.render(context, mouseX, mouseY, delta);
        }



        // Improved scrollbar with smoother appearance
        private void drawScrollbar(DrawContext context) {
            int visibleHeight = this.height - 80 - 25;
            float contentRatio = (float)visibleHeight / Math.max(contentHeight, 1);
            int scrollbarHeight = Math.max((int)(visibleHeight * contentRatio), 32);

            float scrollRatio = 0;
            if (contentHeight > visibleHeight) {
                scrollRatio = (float)Math.abs(scrollOffset) / Math.max(1, contentHeight - visibleHeight);
            }

            int scrollbarY = 80 + (int)((visibleHeight - scrollbarHeight) * scrollRatio);
            int scrollbarX = this.width - 10;

            context.fillGradient(
                    scrollbarX, 80,
                    scrollbarX + 4, this.height - 25,
                    0x40000000, 0x40202020
            );

            context.fillGradient(
                    scrollbarX, scrollbarY,
                    scrollbarX + 4, scrollbarY + scrollbarHeight,
                    0x80BBBBBB, 0x80999999
            );

            context.drawBorder(scrollbarX, scrollbarY, 4, scrollbarHeight, 0x40FFFFFF);
        }

        // Separate method to render color previews
        private void renderColorPreviews(DrawContext context) {
            // Only show color previews if not using manual formatting AND in Default tab
            if (currentTab == 0 && !NamedLootClient.CONFIG.useManualFormatting) {
                // Render name color preview with gradient for a more appealing look
                int namePreviewY = nameColorLabelYPos + 88;
                // Name color
                int nameRed = (int)(NamedLootClient.CONFIG.nameRed * 255);
                int nameGreen = (int)(NamedLootClient.CONFIG.nameGreen * 255);
                int nameBlue = (int)(NamedLootClient.CONFIG.nameBlue * 255);
                int nameColor = (nameRed << 16) | (nameGreen << 8) | nameBlue | 0xFF000000;

                if (namePreviewY + PREVIEW_SIZE > 25 && namePreviewY < this.height - 25) {
                    drawEnhancedColorPreview(context, this.width / 2 + PREVIEW_X_OFFSET, namePreviewY, nameColor);
                }

                // Render count color preview
                int countPreviewY = countColorLabelYPos + 88;
                // Count color
                int countRed = (int)(NamedLootClient.CONFIG.countRed * 255);
                int countGreen = (int)(NamedLootClient.CONFIG.countGreen * 255);
                int countBlue = (int)(NamedLootClient.CONFIG.countBlue * 255);
                int countColor = (countRed << 16) | (countGreen << 8) | countBlue | 0xFF000000;

                if (countPreviewY + PREVIEW_SIZE > 25 && countPreviewY < this.height - 25) {
                    drawEnhancedColorPreview(context, this.width / 2 + PREVIEW_X_OFFSET, countPreviewY, countColor);
                }
            }

            if (currentTab == 0) {
                // Fix the format preview text position
                int formatPreviewY = this.formatField != null ? this.formatField.getY() - 20 : 0;

                // Only render the preview if it's in the visible area
                if (formatPreviewY > 25 && formatPreviewY < this.height - 25) {
                    // Create the preview text
                    MutableText previewText = createPreviewText();

                    // Draw the preview text in a dedicated box with a subtle gradient background
                    int boxWidth = 200;
                    int boxHeight = 20;
                    int boxX = this.width / 2 - boxWidth / 2;
                    int boxY = formatPreviewY - 5;

                    // Draw a pretty gradient background for the preview
                    context.fillGradient(
                            boxX, boxY,
                            boxX + boxWidth, boxY + boxHeight,
                            0x40202040, 0x40404080
                    );

                    // Add subtle border
                    context.drawBorder(boxX, boxY, boxWidth, boxHeight, 0x55AAAAAA);

                    context.drawCenteredTextWithShadow(
                            this.textRenderer,
                            previewText,
                            this.width / 2,
                            formatPreviewY,
                            0xFFFFFFFF
                    );
                }
            }
        }

        private void renderColorCodeReference(DrawContext context) {
            if (currentTab == 0 && NamedLootClient.CONFIG.useManualFormatting) {
                renderColorCodeContentAt(context, this.width / 2 - 100, this.width / 2 + 20,
                        this.formatField.getY() + 26, false);
            } else if (currentTab == 1) {
                // Advanced tab - Check if there's enough space for left side placement

                int referenceBoxHeight = 320;
                int minSpacing = 20;
                int referenceWidth = 230;
                int mainContentWidth = 410;
                int referenceX = this.width / 2 - mainContentWidth / 2 - referenceWidth - minSpacing;
                int referenceY = 120;
                if (!needsInlineColorReference) {

                    context.fillGradient(
                            referenceX - 10, referenceY - 10,
                            referenceX + referenceWidth + 10, referenceY + referenceBoxHeight + 10,
                            0x80000000, 0x80202020
                    );
                    context.drawBorder(referenceX - 10, referenceY - 10, referenceWidth + 20, referenceBoxHeight + 20, 0x88FFFFFF);

                    renderColorCodeContentAt(context, referenceX, referenceX + 110, referenceY, true);
                }
            }
        }

        private void renderColorCodeContentAt(DrawContext context, int leftX, int rightX, int startY, boolean withTitle) {
            int colorY = startY;

            if (withTitle) {
                context.drawTextWithShadow(this.textRenderer,
                        Text.translatable("options.namedloot.format_codes").formatted(Formatting.UNDERLINE),
                        leftX, colorY, 0xFFFFFF);
                colorY += 20;
            } else {
                context.drawTextWithShadow(this.textRenderer,
                        Text.translatable("options.namedloot.format_codes").formatted(Formatting.UNDERLINE),
                        leftX, colorY, 0xFFFFFF);
                colorY += 16;
            }

            // Color codes dalam 2 kolom
            context.drawTextWithShadow(this.textRenderer, Text.literal("&0 ").append(
                    Text.literal("Black").formatted(Formatting.BLACK)), leftX, colorY, 0xFFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.literal("&8 ").append(
                    Text.literal("Dark Gray").formatted(Formatting.DARK_GRAY)), rightX, colorY, 0xFFFFFF);
            colorY += 12;

            context.drawTextWithShadow(this.textRenderer, Text.literal("&1 ").append(
                    Text.literal("Dark Blue").formatted(Formatting.DARK_BLUE)), leftX, colorY, 0xFFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.literal("&9 ").append(
                    Text.literal("Blue").formatted(Formatting.BLUE)), rightX, colorY, 0xFFFFFF);
            colorY += 12;

            context.drawTextWithShadow(this.textRenderer, Text.literal("&2 ").append(
                    Text.literal("Dark Green").formatted(Formatting.DARK_GREEN)), leftX, colorY, 0xFFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.literal("&a ").append(
                    Text.literal("Green").formatted(Formatting.GREEN)), rightX, colorY, 0xFFFFFF);
            colorY += 12;

            context.drawTextWithShadow(this.textRenderer, Text.literal("&3 ").append(
                    Text.literal("Dark Aqua").formatted(Formatting.DARK_AQUA)), leftX, colorY, 0xFFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.literal("&b ").append(
                    Text.literal("Aqua").formatted(Formatting.AQUA)), rightX, colorY, 0xFFFFFF);
            colorY += 12;

            context.drawTextWithShadow(this.textRenderer, Text.literal("&4 ").append(
                    Text.literal("Dark Red").formatted(Formatting.DARK_RED)), leftX, colorY, 0xFFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.literal("&c ").append(
                    Text.literal("Red").formatted(Formatting.RED)), rightX, colorY, 0xFFFFFF);
            colorY += 12;

            context.drawTextWithShadow(this.textRenderer, Text.literal("&5 ").append(
                    Text.literal("Dark Purple").formatted(Formatting.DARK_PURPLE)), leftX, colorY, 0xFFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.literal("&d ").append(
                    Text.literal("Light Purple").formatted(Formatting.LIGHT_PURPLE)), rightX, colorY, 0xFFFFFF);
            colorY += 12;

            context.drawTextWithShadow(this.textRenderer, Text.literal("&6 ").append(
                    Text.literal("Gold").formatted(Formatting.GOLD)), leftX, colorY, 0xFFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.literal("&e ").append(
                    Text.literal("Yellow").formatted(Formatting.YELLOW)), rightX, colorY, 0xFFFFFF);
            colorY += 12;

            context.drawTextWithShadow(this.textRenderer, Text.literal("&7 ").append(
                    Text.literal("Gray").formatted(Formatting.GRAY)), leftX, colorY, 0xFFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.literal("&f ").append(
                    Text.literal("White").formatted(Formatting.WHITE)), rightX, colorY, 0xFFFFFF);
            colorY += 18;

            // Formatting codes section header
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal("Formatting Codes:").formatted(Formatting.UNDERLINE),
                    leftX, colorY, 0xFFFFFF);
            colorY += 16;

            // Formatting codes
            context.drawTextWithShadow(this.textRenderer, Text.literal("&l ").append(
                    Text.literal("Bold").formatted(Formatting.BOLD)), leftX, colorY, 0xFFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.literal("&n ").append(
                    Text.literal("Underline").formatted(Formatting.UNDERLINE)), rightX, colorY, 0xFFFFFF);
            colorY += 12;

            context.drawTextWithShadow(this.textRenderer, Text.literal("&o ").append(
                    Text.literal("Italic").formatted(Formatting.ITALIC)), leftX, colorY, 0xFFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.literal("&m ").append(
                    Text.literal("Strikethrough").formatted(Formatting.STRIKETHROUGH)), rightX, colorY, 0xFFFFFF);
            colorY += 12;

            context.drawTextWithShadow(this.textRenderer, Text.literal("&k ").append(
                    Text.literal("Obfuscated").formatted(Formatting.OBFUSCATED)), leftX, colorY, 0xFFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.literal("&r ").append(
                    Text.literal("Reset")), rightX, colorY, 0xFFFFFF);
        }

        // Enhanced color preview with label and better visuals
        private void drawEnhancedColorPreview(DrawContext context, int x, int y, int color) {
            int previewWidth = PREVIEW_SIZE;
            int previewHeight = PREVIEW_SIZE;

            // Draw outer border (dark with gradient)
            context.fillGradient(
                    x - 2, y - 2,
                    x + previewWidth + 2, y + previewHeight + 2,
                    0xFF222222, 0xFF444444
            );

            // Draw inner border (light)
            context.fill(x - 1, y - 1, x + previewWidth + 1, y + previewHeight + 1, 0xFFAAAAAA);

            // Draw color preview
            context.fill(x, y, x + previewWidth, y + previewHeight, color);

        }


        // Create a separate method for generating the preview text
        private MutableText createPreviewText() {
            // Create example ItemStack for preview
            ItemStack previewItem = new ItemStack(net.minecraft.item.Items.DIAMOND);
            previewItem.setCount(64);

            // Use the same text formatting methods as in WorldRenderEventHandler for consistency
            if (NamedLootClient.CONFIG.useManualFormatting) {
                // For manual formatting, use the same parsing method
                return WorldRenderEventHandler.parseFormattedText(
                        NamedLootClient.CONFIG.textFormat,
                        previewItem,
                        String.valueOf(previewItem.getCount()));
            } else {
                // For automatic coloring, use the same formatting method
                return WorldRenderEventHandler.createAutomaticFormattedText(
                        previewItem,
                        String.valueOf(previewItem.getCount()));
            }
        }
    }
}