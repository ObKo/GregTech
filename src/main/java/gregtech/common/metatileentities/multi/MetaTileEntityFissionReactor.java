package gregtech.common.metatileentities.multi;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockWithDisplayBase;
import gregtech.api.multiblock.BlockPattern;
import gregtech.api.multiblock.BlockWorldState;
import gregtech.api.multiblock.FactoryBlockPattern;
import gregtech.api.multiblock.PatternMatchContext;
import gregtech.api.render.ICubeRenderer;
import gregtech.api.render.Textures;
import gregtech.common.blocks.BlockFireboxCasing;
import gregtech.common.blocks.BlockMetalCasing.MetalCasingType;
import gregtech.common.blocks.MetaBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.*;

import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.function.Predicate;

import static gregtech.api.multiblock.BlockPattern.RelativeDirection.*;

public class MetaTileEntityFissionReactor extends MultiblockWithDisplayBase {
    private static BlockPattern[] structures = new BlockPattern[4];

    private boolean isActive;
    private int size;
    private int depth;
    private IBlockState[][][] components;

    public MetaTileEntityFissionReactor(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId);
    }

    @Override
    protected void updateFormedValid() {
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setBoolean("Active", isActive);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.isActive = data.getBoolean("Active");
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeBoolean(isActive);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.isActive = buf.readBoolean();
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == 100) {
            this.isActive = buf.readBoolean();
            getWorld().checkLight(getPos());
            getHolder().scheduleChunkForRenderUpdate();
        }
    }

    public void setActive(boolean active) {
        this.isActive = active;
        if (!getWorld().isRemote) {
            writeCustomData(100, b -> b.writeBoolean(isActive));
            getWorld().checkLight(getPos());
        }
    }

    public boolean isActive() {
        return isActive;
    }

    public int size() {
        return size;
    }

    public int depth() {
        return depth;
    }

    @Override
    protected void addDisplayText(List<ITextComponent> textList) {
        textList.add(new TextComponentTranslation("gregtech.multiblock.fission_reactor.size",
            size, size, depth));

        if (isStructureFormed()) {
            for (int l = 0; l < depth; l++) {
                for (int r = 0; r < size; r++) {
                    char[] str = new char[size];
                    for (int c = 0; c < size; c++) {
                        if (components[l][r][c] != null)
                            str[c] = 'X';
                        else
                            str[c] = ' ';
                    }
                    textList.add(new TextComponentString(new String(str)));
                }
                textList.add(new TextComponentString(""));
            }
        }
        super.addDisplayText(textList);
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return Textures.CLEAN_STAINLESS_STEEL_CASING;
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        Textures.FISSION_REACTOR_OVERLAY.render(renderState, translation, pipeline, getFrontFacing(), isActive());
    }

    private static Predicate<BlockWorldState> fissionComponentPredicate() {
        return state -> {
            if (state.getBlockState().getBlock().isAir(state.getBlockState(), state.getWorld(), state.getPos()))
                return true;

            if (state.getBlockState() != MetaBlocks.BOILER_FIREBOX_CASING.getState(BlockFireboxCasing.FireboxCasingType.TITANIUM_FIREBOX))
                return false;

            BlockPos pos = state.getPos();
            Set<Pair<BlockPos, IBlockState>> comp = state.getMatchContext().getOrCreate("FissionComponents", HashSet::new);
            comp.add(Pair.of(pos, state.getBlockState()));
            return true;
        };
    }

    @Override
    protected BlockPattern createStructurePattern() {
        structures[0] = FactoryBlockPattern.start(RIGHT, FRONT, UP)
            .aisle("XXX", "XXX", "XXX")
            .aisle("XXX", "X#X", "XXX").setRepeatable(1, 5)
            .aisle("XXX", "XYX", "XXX")
            .where('X', statePredicate(MetaBlocks.METAL_CASING.getState(MetalCasingType.STAINLESS_CLEAN)))
            .where('#', fissionComponentPredicate())
            .where('Y', selfPredicate())
            .build();
        structures[1] = FactoryBlockPattern.start(RIGHT, FRONT, UP)
            .aisle("XXXXX", "XXXXX", "XXXXX", "XXXXX", "XXXXX")
            .aisle("XXXXX", "X###X", "X###X", "X###X", "XXXXX").setRepeatable(1, 5)
            .aisle("XXXXX", "XXXXX", "XXYXX", "XXXXX", "XXXXX")
            .where('X', statePredicate(MetaBlocks.METAL_CASING.getState(MetalCasingType.STAINLESS_CLEAN)))
            .where('#', fissionComponentPredicate())
            .where('Y', selfPredicate())
            .build();
        structures[2] = FactoryBlockPattern.start(RIGHT, FRONT, UP)
            .aisle("XXXXXXX", "XXXXXXX", "XXXXXXX", "XXXXXXX", "XXXXXXX", "XXXXXXX", "XXXXXXX")
            .aisle("XXXXXXX", "X#####X", "X#####X", "X#####X", "X#####X", "X#####X", "XXXXXXX").setRepeatable(1, 5)
            .aisle("XXXXXXX", "XXXXXXX", "XXXXXXX", "XXXYXXX", "XXXXXXX", "XXXXXXX", "XXXXXXX")
            .where('X', statePredicate(MetaBlocks.METAL_CASING.getState(MetalCasingType.STAINLESS_CLEAN)))
            .where('#', fissionComponentPredicate())
            .where('Y', selfPredicate())
            .build();
        structures[3] = FactoryBlockPattern.start(RIGHT, FRONT, UP)
            .aisle("XXXXXXXXX", "XXXXXXXXX", "XXXXXXXXX", "XXXXXXXXX", "XXXXXXXXX", "XXXXXXXXX", "XXXXXXXXX", "XXXXXXXXX", "XXXXXXXXX")
            .aisle("XXXXXXXXX", "X#######X", "X#######X", "X#######X", "X#######X", "X#######X", "X#######X", "X#######X", "XXXXXXXXX").setRepeatable(1, 5)
            .aisle("XXXXXXXXX", "XXXXXXXXX", "XXXXXXXXX", "XXXXXXXXX", "XXXXYXXXX", "XXXXXXXXX", "XXXXXXXXX", "XXXXXXXXX", "XXXXXXXXX")
            .where('X', statePredicate(MetaBlocks.METAL_CASING.getState(MetalCasingType.STAINLESS_CLEAN)))
            .where('#', fissionComponentPredicate())
            .where('Y', selfPredicate())
            .build();
        return structures[0];
    }

    @Override
    public MetaTileEntity createMetaTileEntity(MetaTileEntityHolder holder) {
        return new MetaTileEntityFissionReactor(metaTileEntityId);
    }

    private void formReactorContext(PatternMatchContext pattern)
    {
        Set<Pair<BlockPos, IBlockState>> comps = pattern.getOrCreate("FissionComponents", HashSet::new);
        this.components = new IBlockState[depth][size][size];
        for (Pair<BlockPos, IBlockState> c : comps) {
            BlockPos bp = c.getLeft();
            BlockPos sp = getPos();
            int layer = sp.getY() - bp.getY() - 1;
            int row = bp.getZ() - sp.getZ() + size / 2;
            int col = bp.getX() - sp.getX() + size / 2;

            if (layer < 0 || layer >= depth || row < 0 || row >= size || col < 0 || col >= size)
                continue;

            this.components[layer][row][col] = c.getRight();
        }
    }

    private void invalidateReactorContext()
    {
        this.components = null;
    }

    @Override
    protected void formStructure(PatternMatchContext context) {
        super.formStructure(context);
        this.size = context.getInt("palmLength") - 2;
        this.depth = context.getInt("fingerLength") - 2;
        formReactorContext(context);
    }

    @Override
    public void invalidateStructure() {
        invalidateReactorContext();
        this.size = 0;
        this.depth = 0;
        super.invalidateStructure();
    }

    @Override
    protected void checkStructurePattern() {
        super.checkStructurePattern();
        for (int i = 0; !isStructureFormed() && (i < 4); i++) {
            this.structurePattern = structures[i];
            super.checkStructurePattern();
        }
    }
}
