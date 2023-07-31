package com.goby56.wakes.utils;

import com.goby56.wakes.WakesClient;
import com.goby56.wakes.config.WakesConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.world.ClientWorld;
import org.antlr.v4.runtime.misc.OrderedHashSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class WakeHandler {
    public final int MAX_QUERY_RANGE = 10;

    private static WakeHandler INSTANCE;

    private final ArrayList<QuadTree<WakeNode>> trees;
    private final ArrayList<OrderedHashSet<WakeNode>> toBeInserted;
    public boolean resetScheduled = false;
    private WakesConfig.Resolution newResolution = null;
    private final int minY;
    private final int maxY;

    public int glWakeTexId = -1;
    public long wakeImgPtr = -1;
    public int glFoamTexId = -1;
    public long foamImgPtr = -1;

    private WakeHandler(ClientWorld world) {
        WakeNode.calculateAlpha();
        this.minY = world.getBottomY();
        this.maxY = world.getTopY();
        int worldHeight = this.maxY - this.minY;
        this.trees = new ArrayList<>(worldHeight);
        this.toBeInserted = new ArrayList<>(worldHeight);
        for (int i = 0; i < worldHeight; i++) {
            this.trees.add(null);
            this.toBeInserted.add(new OrderedHashSet<>());
        }
    }

    public static WakeHandler getInstance() {
        if (INSTANCE == null) {
            if (MinecraftClient.getInstance().world == null) {
                return null;
            }
            INSTANCE = new WakeHandler(MinecraftClient.getInstance().world);
        }
        return INSTANCE;
    }

    public void tick() {
        for (int i = 0; i < this.maxY - this.minY; i++) {
            if (this.resetScheduled) {
                this.toBeInserted.get(i).clear();
                continue;
            }
            QuadTree<WakeNode> tree = this.trees.get(i);
            if (tree != null) {
                tree.tick();

                OrderedHashSet<WakeNode> pendingNodes = this.toBeInserted.get(i);
                // Iterating through current pending nodes - new nodes may be added
                // at the same time, thus preventing CME
                for (int j = pendingNodes.size() - 1; j >= 0; --j) {
                    tree.insert(pendingNodes.get(j));
                    pendingNodes.remove(j);
                }
            }
        }
        if (this.resetScheduled) {
            this.changeResolution();
        }
    }

    public void insert(WakeNode node) {
        if (this.resetScheduled) return;
        int i = this.getArrayIndex((int) node.height);
        if (i < 0) return;

        if (this.trees.get(i) == null) {
            this.trees.add(i, new QuadTree<>(0, 0, 30000000));
        }

        this.toBeInserted.get(i).add(node);
    }

    public ArrayList<WakeNode> getVisible(Frustum frustum) {
        ArrayList<WakeNode> foundNodes = new ArrayList<>();
        for (int i = 0; i < this.maxY - this.minY; i++) {
            if (this.trees.get(i) != null) {
                this.trees.get(i).query(frustum, i + this.minY, foundNodes);
            }
        }
        return foundNodes;
    }

    public ArrayList<WakeNode> getNearby(int x, int y, int z) {
        ArrayList<WakeNode> foundNodes = new ArrayList<>();
        int i = this.getArrayIndex(y);
        if (i < 0) return foundNodes;
        if (this.trees.get(i) == null) {
            return foundNodes;
        }
        QuadTree.Circle range = new QuadTree.Circle(x, z, this.MAX_QUERY_RANGE);
        this.trees.get(i).query(range, foundNodes);
        return foundNodes;
    }

    private int getArrayIndex(int height) {
        if (height < this.minY || height > this.maxY) {
            return -1;
        }
        return height + Math.abs(this.minY);
    }

    public static void scheduleResolutionChange(WakesConfig.Resolution newRes) {
        WakeHandler wakeHandler = WakeHandler.getInstance();
        if (wakeHandler == null) {
            WakesClient.CONFIG_INSTANCE.wakeResolution = newRes;
            WakeNode.calculateAlpha();
            return;
        }
        wakeHandler.resetScheduled = true;
        wakeHandler.newResolution = newRes;
    }

    private void changeResolution() {
        this.reset();
        this.glWakeTexId = -1;
        this.wakeImgPtr = -1;
        this.glFoamTexId = -1;
        this.foamImgPtr = -1;
        WakesClient.CONFIG_INSTANCE.wakeResolution = this.newResolution;
        WakeNode.calculateAlpha();
        this.resetScheduled = false;
        this.newResolution = null;
    }

    private void reset() {
        for (int i = 0; i < this.maxY - this.minY; i++) {
            QuadTree<WakeNode> tree = this.trees.get(i);
            if (tree != null) {
                tree.prune();
            }
        }
    }
}
