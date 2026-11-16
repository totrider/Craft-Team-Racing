package com.ctrcraft.client;

import java.util.ArrayList;
import java.util.List;

import com.ctrcraft.MusicSheetData;
import com.ctrcraft.net.MusicSheetSavePayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The track music sheet's screen: a text field with the title the track shows, and below it a grid of the tracks this client has a sound for (the default one and "none" first), by name.
 * Picking one fills the title with its name when the title was empty or the name of the last pick; the title can be anything and is what other players see, so a track whose sound only
 * you have is still named for everyone. The id of a track that this client has no sound for (set by someone else) is kept as it is.
 */
public class MusicSheetScreen extends Screen {
    private static final int COLUMNS = 3;
    private static final int CELL_W = 104;
    private static final int CELL_H = 22;
    private static final int VISIBLE_ROWS = 5;

    private final InteractionHand hand;
    private String id;
    private String lastAutoTitle;
    private String title;
    private boolean asDefault;
    private int rowOffset;
    private EditBox titleBox;
    private final List<String> tracks = new ArrayList<>();

    public MusicSheetScreen(InteractionHand hand, MusicSheetData data) {
        super(Component.translatable("screen.ctrcraft.music.title"));
        this.hand = hand;
        this.id = data.id();
        this.title = data.title();
        this.asDefault = data.asDefault();
        this.lastAutoTitle = MusicTitles.nameOf(id).getString();
    }

    @Override
    protected void init() {
        int left = width / 2 - COLUMNS * CELL_W / 2;
        tracks.clear();
        tracks.add("");
        tracks.add(MusicSheetData.NONE);
        tracks.addAll(MusicTitles.available());
        if (!tracks.contains(id)) tracks.add(id);          // a track set by someone whose sound this client does not have
        titleBox = new EditBox(font, left + 2, 24, COLUMNS * CELL_W - 4, 18, Component.translatable("screen.ctrcraft.music.name"));
        titleBox.setMaxLength(48);
        titleBox.setHint(Component.translatable("screen.ctrcraft.music.name_hint"));
        titleBox.setValue(title);
        titleBox.setResponder(v -> title = v);
        addRenderableWidget(titleBox);
        addRenderableWidget(Button.builder(defaultLabel(), b -> {
            asDefault = !asDefault;
            b.setMessage(defaultLabel());
        }).bounds(left + 2, 46, COLUMNS * CELL_W - 4, 20).build());
        int rows = (tracks.size() + COLUMNS - 1) / COLUMNS;
        rowOffset = Math.max(0, Math.min(rowOffset, rows - VISIBLE_ROWS));
        for (int r = 0; r < VISIBLE_ROWS; r++) {
            for (int c = 0; c < COLUMNS; c++) {
                int i = (r + rowOffset) * COLUMNS + c;
                if (i >= tracks.size()) continue;
                String track = tracks.get(i);
                boolean selected = track.equals(id);
                Component label = MusicTitles.nameOf(track);
                if (selected) label = Component.literal("> ").append(label).append(" <");
                else if (!MusicTitles.playable(track)) label = label.copy().withStyle(net.minecraft.ChatFormatting.GRAY);
                addRenderableWidget(Button.builder(label, b -> pick(track)).bounds(left + c * CELL_W + 2, 72 + r * CELL_H, CELL_W - 4, CELL_H - 2).build());
            }
        }
        int y = 72 + VISIBLE_ROWS * CELL_H + 4;
        addRenderableWidget(Button.builder(Component.translatable("screen.ctrcraft.rulebook.done"), b -> save()).bounds(width / 2 - 102, y, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(width / 2 + 2, y, 100, 20).build());
    }

    private void pick(String track) {
        String name = MusicTitles.nameOf(track).getString();
        if (title.isEmpty() || title.equals(lastAutoTitle)) title = track.isEmpty() ? "" : name;
        lastAutoTitle = track.isEmpty() ? "" : name;
        id = track;
        rebuildWidgets();
    }

    private Component defaultLabel() {
        return Component.translatable("screen.ctrcraft.rulebook.apply_as", Component.translatable(asDefault ? "screen.ctrcraft.rulebook.as_default" : "screen.ctrcraft.rulebook.as_temporary"));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        int rows = (tracks.size() + COLUMNS - 1) / COLUMNS;
        if (rows > VISIBLE_ROWS && scrollY != 0) {
            int next = Math.max(0, Math.min(rows - VISIBLE_ROWS, rowOffset - (int) Math.signum(scrollY)));
            if (next != rowOffset) {
                rowOffset = next;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    private void save() {
        PacketDistributor.sendToServer(new MusicSheetSavePayload(hand.ordinal(), new MusicSheetData(id, title.trim(), asDefault)));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title(), width / 2, 8, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("screen.ctrcraft.music.hint"), width / 2, 72 + VISIBLE_ROWS * CELL_H + 28, 0xA0A0A0);
    }

    private Component title() {
        return super.getTitle();
    }
}
