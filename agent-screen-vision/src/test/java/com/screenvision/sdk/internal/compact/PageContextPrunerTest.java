package com.screenvision.sdk.internal.compact;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.screenvision.sdk.CompactAnalysisOptions;
import com.screenvision.sdk.RecognitionMode;
import com.screenvision.sdk.TaskContext;
import com.screenvision.sdk.model.BoundingBox;
import com.screenvision.sdk.model.CompactDebugInfo;
import com.screenvision.sdk.model.CompactListItem;
import com.screenvision.sdk.model.CompactPageAnalysisResult;
import com.screenvision.sdk.model.CompactSection;
import com.screenvision.sdk.model.CompactSectionType;
import com.screenvision.sdk.model.CompactTextBlock;
import com.screenvision.sdk.model.CompactUiElement;
import com.screenvision.sdk.model.PageAnalysisResult;
import com.screenvision.sdk.model.PageSize;
import com.screenvision.sdk.model.RecognizedTextBlock;
import com.screenvision.sdk.model.RecognizedUiElement;
import com.screenvision.sdk.model.UiElementType;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PageContextPrunerTest {
    private static final PageSize PAGE = new PageSize(1080, 1920);

    private final PageContextPruner pruner = new PageContextPruner();

    @Test
    public void prune_loginPage_keepsPrimaryControlsAndRemovesDuplicateButtonText() {
        PageAnalysisResult raw = result(
                Arrays.asList(
                        text("Sign In", 420, 220, 680, 300),
                        text("Phone", 120, 560, 340, 610),
                        text("Login", 420, 980, 660, 1040)
                ),
                Arrays.asList(
                        control(UiElementType.INPUT, "input", 100, 620, 980, 760),
                        control(UiElementType.BUTTON, "button", 160, 930, 920, 1080)
                )
        );

        CompactPageAnalysisResult compact = pruner.prune(raw);

        assertTrue(hasControlType(compact, UiElementType.INPUT));
        assertTrue(hasControl(compact, UiElementType.BUTTON, "Login"));
        assertTrue(hasText(compact, "Phone"));
        assertFalse(hasText(compact, "Login"));
    }

    @Test
    public void prune_settingsPage_keepsSwitchAndPairedLabel() {
        PageAnalysisResult raw = result(
                Arrays.asList(
                        text("Settings", 420, 120, 700, 200),
                        text("Notifications", 120, 520, 640, 580)
                ),
                Arrays.asList(
                        control(UiElementType.SWITCH, "switch", 720, 500, 900, 590)
                )
        );

        CompactPageAnalysisResult compact = pruner.prune(raw);

        CompactUiElement switchElement = findControl(compact, UiElementType.SWITCH, "Notifications");
        assertNotNull(switchElement);
        assertTrue(hasText(compact, "Notifications"));
    }

    @Test
    public void prune_bottomTabs_createsBottomBarSection() {
        PageAnalysisResult raw = result(
                Arrays.asList(
                        text("Home", 110, 1750, 240, 1810),
                        text("Inbox", 470, 1750, 620, 1810),
                        text("Me", 830, 1750, 920, 1810)
                ),
                Arrays.asList(
                        control(UiElementType.TAB, "Home", 70, 1680, 290, 1870),
                        control(UiElementType.TAB, "Inbox", 420, 1680, 660, 1870),
                        control(UiElementType.TAB, "Me", 780, 1680, 1010, 1870)
                )
        );

        CompactPageAnalysisResult compact = pruner.prune(raw);

        assertTrue(hasSection(compact, CompactSectionType.BOTTOM_BAR));
        assertTrue(countControls(compact, UiElementType.TAB) >= 3);
    }

    @Test
    public void prune_denseList_collapsesOverflowRows() {
        List<RecognizedUiElement> controls = new ArrayList<>();
        List<RecognizedTextBlock> texts = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            int top = 420 + index * 220;
            controls.add(control(UiElementType.CARD, "card", 80, top, 1000, top + 180));
            texts.add(text("Order " + index, 120, top + 40, 340, top + 100));
        }

        CompactPageAnalysisResult compact = pruner.prune(result(texts, controls));
        CompactSection denseSection = findSection(compact, CompactSectionType.DENSE_LIST);

        assertNotNull(denseSection);
        assertTrue(denseSection.getCollapsedItemCount() > 0);
    }

    @Test
    public void prune_taskGoal_keepsMatchedDenseListItem() {
        List<RecognizedUiElement> controls = new ArrayList<>();
        List<RecognizedTextBlock> texts = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            int top = 380 + index * 220;
            controls.add(control(UiElementType.BUTTON, "button", 80, top, 1000, top + 170));
            texts.add(text(index == 3 ? "Phone Support" : "Common Entry " + index, 140, top + 48, 580, top + 104));
        }

        PageAnalysisResult raw = result(texts, controls);
        CompactPageAnalysisResult withoutGoal = pruner.prune(raw);
        CompactPageAnalysisResult withGoal = pruner.prune(
                raw,
                CompactAnalysisOptions.newBuilder().build(),
                new TaskContext("phone")
        );

        assertTrue(hasControl(withGoal, UiElementType.BUTTON, "Phone Support"));
        assertTrue(withGoal.getItems().size() >= withoutGoal.getItems().size());
    }

    @Test
    public void prune_conversationList_emitsPerItemGrouping() {
        CompactPageAnalysisResult compact = pruner.prune(conversationListResult(4, RecognitionMode.TEXT_AND_UI, true));

        assertTrue(hasSection(compact, CompactSectionType.DENSE_LIST));
        assertTrue(compact.getItems().size() >= 4);
        assertTrue(countControls(compact, UiElementType.AVATAR) >= 3);
        assertTrue(hasListItem(compact, "Alice 0"));
        assertTrue(hasControlWithItem(compact, UiElementType.AVATAR));
    }

    @Test
    public void prune_denseList_sectionBbox_coversAllItems() {
        CompactPageAnalysisResult compact = pruner.prune(conversationListResult(5, RecognitionMode.TEXT_AND_UI, true));
        CompactSection denseSection = findSection(compact, CompactSectionType.DENSE_LIST);

        assertNotNull(denseSection);
        assertTrue(compact.getItems().size() >= 5);
        for (CompactListItem item : compact.getItems()) {
            if (denseSection.getId().equals(item.getSectionId())) {
                assertTrue(isInside(item.getBoundingBox(), denseSection.getBoundingBox()));
            }
        }
    }

    @Test
    public void prune_uiOnlyContactList_groupsRowsByLeadingIcons() {
        CompactPageAnalysisResult compact = pruner.prune(conversationListResult(5, RecognitionMode.UI_ONLY, false));
        CompactSection denseSection = findSection(compact, CompactSectionType.DENSE_LIST);

        assertNotNull(denseSection);
        assertTrue(compact.getItems().size() >= 5);
        for (CompactListItem item : compact.getItems()) {
            assertFalse(item.getControlIds().isEmpty());
            assertTrue(item.getBoundingBox().getHeight() < 220);
            assertTrue(isInside(item.getBoundingBox(), denseSection.getBoundingBox()));
        }
    }

    @Test
    public void prune_debugMetadata_emitsSectionSourcesRowsAndDropSummary() {
        CompactPageAnalysisResult compact = pruner.prune(
                conversationListResult(5, RecognitionMode.TEXT_AND_UI, true),
                CompactAnalysisOptions.newBuilder().setEnableDebugMetadata(true).build(),
                TaskContext.empty()
        );

        CompactDebugInfo debugInfo = compact.getDebugInfo();
        assertNotNull(debugInfo);
        assertFalse(debugInfo.getSectionSources().isEmpty());
        assertFalse(debugInfo.getRows().isEmpty());
        assertNotNull(debugInfo.getDropSummary());
        assertTrue(debugInfo.getRows().size() >= compact.getItems().size());
    }

    @Test
    public void prune_headerIcons_keepsGroupedIconButtons() {
        PageAnalysisResult raw = result(
                Arrays.asList(
                        text("Chats", 430, 110, 650, 180)
                ),
                Arrays.asList(
                        control(UiElementType.ICON_BUTTON, "search", 820, 96, 900, 176),
                        control(UiElementType.ICON_BUTTON, "add", 940, 96, 1020, 176)
                )
        );

        CompactPageAnalysisResult compact = pruner.prune(raw);

        assertTrue(countControls(compact, UiElementType.ICON_BUTTON) >= 2);
        assertTrue(hasControl(compact, UiElementType.ICON_BUTTON, "search"));
        assertTrue(hasControl(compact, UiElementType.ICON_BUTTON, "add"));
    }

    @Test
    public void prune_cardFeed_keepsPrimaryCardAndPrimaryAction() {
        PageAnalysisResult raw = result(
                Arrays.asList(
                        text("Recommended", 120, 190, 400, 250),
                        text("Starter Kit", 120, 410, 430, 470),
                        text("Continue", 430, 760, 650, 820)
                ),
                Arrays.asList(
                        control(UiElementType.CARD, "card", 80, 320, 1000, 900),
                        control(UiElementType.IMAGE, "hero", 100, 340, 980, 620),
                        control(UiElementType.BUTTON, "Continue", 300, 710, 780, 860),
                        control(UiElementType.AVATAR, "avatar", 880, 210, 980, 310)
                )
        );

        CompactPageAnalysisResult compact = pruner.prune(raw);

        assertTrue(hasControlType(compact, UiElementType.CARD));
        assertTrue(hasControl(compact, UiElementType.BUTTON, "Continue"));
    }

    @Test
    public void prune_navigationPage_keepsHeaderAndBottomBarActions() {
        PageAnalysisResult raw = result(
                Arrays.asList(
                        text("Chats", 430, 110, 650, 180),
                        text("Home", 110, 1750, 240, 1810),
                        text("Inbox", 470, 1750, 620, 1810),
                        text("Me", 830, 1750, 920, 1810)
                ),
                Arrays.asList(
                        control(UiElementType.ICON_BUTTON, "search", 820, 96, 900, 176),
                        control(UiElementType.ICON_BUTTON, "add", 940, 96, 1020, 176),
                        control(UiElementType.CARD, "card", 80, 320, 1000, 900),
                        control(UiElementType.TAB, "Home", 70, 1680, 290, 1870),
                        control(UiElementType.TAB, "Inbox", 420, 1680, 660, 1870),
                        control(UiElementType.TAB, "Me", 780, 1680, 1010, 1870)
                )
        );

        CompactPageAnalysisResult compact = pruner.prune(raw);

        assertTrue(hasSection(compact, CompactSectionType.BOTTOM_BAR));
        assertTrue(countControls(compact, UiElementType.ICON_BUTTON) >= 2);
        assertTrue(countControls(compact, UiElementType.TAB) >= 3);
    }

    @Test
    public void prune_collapsedDenseList_itemsKeepRowSizedBbox() {
        CompactPageAnalysisResult compact = pruner.prune(conversationListResult(7, RecognitionMode.UI_ONLY, false));
        CompactSection denseSection = findSection(compact, CompactSectionType.DENSE_LIST);

        assertNotNull(denseSection);
        assertTrue(denseSection.getCollapsedItemCount() > 0);
        for (CompactListItem item : compact.getItems()) {
            if (denseSection.getId().equals(item.getSectionId())) {
                assertTrue(item.getBoundingBox().getWidth() > PAGE.getWidth() * 0.40f);
            }
        }
    }

    @Test
    public void prune_bottomBar_staysAtPageBottomAndDoesNotSwallowDenseList() {
        CompactPageAnalysisResult compact = pruner.prune(conversationListWithBottomTabsResult(7, RecognitionMode.UI_ONLY, false));
        CompactSection denseSection = findSection(compact, CompactSectionType.DENSE_LIST);
        CompactSection bottomBar = findSection(compact, CompactSectionType.BOTTOM_BAR);

        assertNotNull(denseSection);
        assertNotNull(bottomBar);
        assertTrue(bottomBar.getBoundingBox().getTop() > PAGE.getHeight() * 0.78f);
        assertTrue(denseSection.getBoundingBox().getBottom() < bottomBar.getBoundingBox().getTop());
    }

    @Test
    public void prune_summaryPrefersDenseListOverBottomBar() {
        CompactPageAnalysisResult compact = pruner.prune(conversationListWithBottomTabsResult(7, RecognitionMode.UI_ONLY, false));

        assertTrue(compact.getSummary().contains("dense_list"));
    }

    @Test
    public void prune_headerNoise_filtersFalsePositiveInputsAndToggles() {
        PageAnalysisResult raw = result(
                Arrays.asList(
                        text("Messages", 120, 120, 220, 168)
                ),
                Arrays.asList(
                        control(UiElementType.SWITCH, "10:39", 202, 52, 236, 71),
                        control(UiElementType.CHECKBOX, "Messages", 73, 126, 111, 164),
                        control(UiElementType.BUTTON, "button", 107, 39, 214, 83),
                        control(UiElementType.ICON_BUTTON, "search", 700, 100, 800, 200)
                )
        );

        CompactPageAnalysisResult compact = pruner.prune(raw);

        assertTrue(hasControl(compact, UiElementType.ICON_BUTTON, "search"));
        assertFalse(hasControlType(compact, UiElementType.SWITCH));
        assertFalse(hasControlType(compact, UiElementType.CHECKBOX));
    }

    private PageAnalysisResult conversationListResult(int count, RecognitionMode recognitionMode, boolean includeTexts) {
        List<RecognizedUiElement> controls = new ArrayList<>();
        List<RecognizedTextBlock> texts = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            int top = 300 + index * 160;
            controls.add(control(UiElementType.AVATAR, "avatar", 60, top, 160, top + 100));
            controls.add(control(UiElementType.INPUT, "row", 190, top - 10, 1000, top + 120));
            if (includeTexts) {
                texts.add(text("Alice " + index, 220, top, 500, top + 40));
                texts.add(text("Project update " + index, 220, top + 52, 760, top + 104));
                texts.add(text("10:0" + index, 860, top + 4, 980, top + 40));
            }
        }
        return result(texts, controls, recognitionMode);
    }

    private PageAnalysisResult conversationListWithBottomTabsResult(int count, RecognitionMode recognitionMode, boolean includeTexts) {
        List<RecognizedUiElement> controls = new ArrayList<>();
        List<RecognizedTextBlock> texts = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            int top = 300 + index * 160;
            controls.add(control(UiElementType.AVATAR, "avatar", 60, top, 160, top + 100));
            controls.add(control(UiElementType.INPUT, "row", 190, top - 10, 1000, top + 120));
            if (includeTexts) {
                texts.add(text("Alice " + index, 220, top, 500, top + 40));
                texts.add(text("Project update " + index, 220, top + 52, 760, top + 104));
            }
        }
        controls.add(control(UiElementType.TAB, "Home", 70, 1680, 290, 1870));
        controls.add(control(UiElementType.TAB, "Inbox", 420, 1680, 660, 1870));
        controls.add(control(UiElementType.TAB, "Me", 780, 1680, 1010, 1870));
        if (includeTexts) {
            texts.add(text("Home", 110, 1750, 240, 1810));
            texts.add(text("Inbox", 470, 1750, 620, 1810));
            texts.add(text("Me", 830, 1750, 920, 1810));
        }
        return result(texts, controls, recognitionMode);
    }

    private PageAnalysisResult result(List<RecognizedTextBlock> texts, List<RecognizedUiElement> controls) {
        return result(texts, controls, RecognitionMode.TEXT_AND_UI);
    }

    private PageAnalysisResult result(List<RecognizedTextBlock> texts, List<RecognizedUiElement> controls, RecognitionMode recognitionMode) {
        return new PageAnalysisResult(texts, controls, PAGE, 123L, recognitionMode);
    }

    private RecognizedTextBlock text(String value, int left, int top, int right, int bottom) {
        return new RecognizedTextBlock(value, new BoundingBox(left, top, right, bottom), 0.96f);
    }

    private RecognizedUiElement control(UiElementType type, String label, int left, int top, int right, int bottom) {
        return new RecognizedUiElement(type, new BoundingBox(left, top, right, bottom), 0.92f, label);
    }

    private boolean hasSection(CompactPageAnalysisResult result, CompactSectionType type) {
        return findSection(result, type) != null;
    }

    private CompactSection findSection(CompactPageAnalysisResult result, CompactSectionType type) {
        for (CompactSection section : result.getSections()) {
            if (section.getType() == type) {
                return section;
            }
        }
        return null;
    }

    private boolean hasListItem(CompactPageAnalysisResult result, String summaryContains) {
        for (CompactListItem item : result.getItems()) {
            if (item.getSummaryText().contains(summaryContains)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasText(CompactPageAnalysisResult result, String expectedText) {
        String normalized = expectedText.trim();
        return result.getTextBlocks().stream().anyMatch(text -> normalized.equals(text.getText()));
    }

    private boolean hasControlType(CompactPageAnalysisResult result, UiElementType type) {
        for (CompactUiElement element : result.getUiElements()) {
            if (element.getType() == type) {
                return true;
            }
        }
        return false;
    }

    private boolean hasControl(CompactPageAnalysisResult result, UiElementType type, String labelContains) {
        return findControl(result, type, labelContains) != null;
    }

    private boolean hasControlWithItem(CompactPageAnalysisResult result, UiElementType type) {
        for (CompactUiElement element : result.getUiElements()) {
            if (element.getType() == type && !element.getItemId().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private CompactUiElement findControl(CompactPageAnalysisResult result, UiElementType type, String labelContains) {
        String expected = labelContains == null ? "" : labelContains.trim();
        for (CompactUiElement element : result.getUiElements()) {
            if (element.getType() != type) {
                continue;
            }
            if (expected.isEmpty() || element.getLabel().contains(expected)) {
                return element;
            }
        }
        return null;
    }

    private int countControls(CompactPageAnalysisResult result, UiElementType type) {
        int count = 0;
        for (CompactUiElement element : result.getUiElements()) {
            if (element.getType() == type) {
                count++;
            }
        }
        return count;
    }

    private boolean isInside(BoundingBox inner, BoundingBox outer) {
        return inner.getLeft() >= outer.getLeft()
                && inner.getTop() >= outer.getTop()
                && inner.getRight() <= outer.getRight()
                && inner.getBottom() <= outer.getBottom();
    }
}