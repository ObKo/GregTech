package gregtech.common.metatileentities.multi;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import gregtech.api.GTValues;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.capability.impl.FuelRecipeLogic;
import gregtech.api.capability.impl.ItemHandlerList;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockWithDisplayBase;
import gregtech.api.multiblock.BlockPattern;
import gregtech.api.multiblock.FactoryBlockPattern;
import gregtech.api.multiblock.PatternMatchContext;
import gregtech.api.recipes.ModHandler;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.recipes.FuelRecipe;
import gregtech.api.render.ICubeRenderer;
import gregtech.api.render.SimpleCubeRenderer;
import gregtech.api.render.Textures;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.material.type.Material;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.unification.stack.UnificationEntry;
import gregtech.api.util.GTUtility;
import gregtech.common.blocks.BlockBoilerCasing.BoilerCasingType;
import gregtech.common.blocks.BlockFireboxCasing;
import gregtech.common.blocks.BlockFireboxCasing.FireboxCasingType;
import gregtech.common.blocks.BlockFissionControl;
import gregtech.common.blocks.BlockFissionCore;
import gregtech.common.blocks.BlockMetalCasing.MetalCasingType;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.items.MetaItems;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.oredict.OreIngredient;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MetaTileEntityFission extends MultiblockWithDisplayBase {

    private static final int CONSUMPTION_MULTIPLIER = 100;

    public enum FissionType {
        BWR(900, 1.0f, 600,
            MetaBlocks.METAL_CASING.getState(MetalCasingType.STAINLESS_CLEAN),
            MetaBlocks.FISSION_CORE.getState(BlockFissionCore.FissionCoreType.BWR_FISSION_CORE),
            MetaBlocks.FISSION_CONTROL.getState(BlockFissionControl.FissionControlType.BWR_FISSION_CONTROL),
            Textures.CLEAN_STAINLESS_STEEL_CASING),

        PWR(1600, 1.6f, 800,
            MetaBlocks.METAL_CASING.getState(MetalCasingType.TITANIUM_STABLE),
            MetaBlocks.FISSION_CORE.getState(BlockFissionCore.FissionCoreType.PWR_FISSION_CORE),
            MetaBlocks.FISSION_CONTROL.getState(BlockFissionControl.FissionControlType.PWR_FISSION_CONTROL),
            Textures.STABLE_TITANIUM_CASING);

        public final int baseSteamOutput;
        public final float fuelConsumptionMultiplier;
        public final int maxTemperature;
        public final IBlockState casingState;
        public final IBlockState coreState;
        public final IBlockState controlState;
        public final ICubeRenderer solidCasingRenderer;

        FissionType(int baseSteamOutput, float fuelConsumptionMultiplier, int maxTemperature, IBlockState casingState, IBlockState coreState, IBlockState controlState,
                   ICubeRenderer solidCasingRenderer) {
            this.baseSteamOutput = baseSteamOutput;
            this.fuelConsumptionMultiplier = fuelConsumptionMultiplier;
            this.maxTemperature = maxTemperature;
            this.casingState = casingState;
            this.coreState = coreState;
            this.controlState = controlState;
            this.solidCasingRenderer = solidCasingRenderer;
        }
    }

    public final FissionType fissionType;

    private int currentTemperature;
    private int fuelBurnTicksLeft;
    private boolean isActive;
    private boolean wasActiveAndNeedsUpdate;
    private boolean hasNoWater;

    private FluidTankList fluidImportInventory;
    private ItemHandlerList itemImportInventory;
    private FluidTankList steamOutputTank;

    public MetaTileEntityFission(ResourceLocation metaTileEntityId, FissionType fissionType) {
        super(metaTileEntityId);
        this.fissionType = fissionType;
        reinitializeStructurePattern();
    }

    @Override
    public MetaTileEntity createMetaTileEntity(MetaTileEntityHolder holder) {
        return new MetaTileEntityFission(metaTileEntityId, fissionType);
    }

    @Override
    protected void formStructure(PatternMatchContext context) {
        super.formStructure(context);
        this.fluidImportInventory = new FluidTankList(true, getAbilities(MultiblockAbility.IMPORT_FLUIDS));
        this.itemImportInventory = new ItemHandlerList(getAbilities(MultiblockAbility.IMPORT_ITEMS));
        this.steamOutputTank = new FluidTankList(true, getAbilities(MultiblockAbility.EXPORT_FLUIDS));
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        this.fluidImportInventory = new FluidTankList(true);
        this.itemImportInventory = new ItemHandlerList(Collections.emptyList());
        this.steamOutputTank = new FluidTankList(true);
        this.currentTemperature = 0; //reset temperature
        this.fuelBurnTicksLeft = 0;
        this.hasNoWater = false;
        this.isActive = false;
    }

    @Override
    protected void addDisplayText(List<ITextComponent> textList) {
        if (isStructureFormed()) {
            textList.add(new TextComponentTranslation("gregtech.multiblock.fission.temperature",
                currentTemperature, fissionType.maxTemperature));
            int steamOutput = 0;
            if (currentTemperature >= 100) {
                double outputMultiplier = currentTemperature / (fissionType.maxTemperature * 1.0);
                steamOutput = (int) (fissionType.baseSteamOutput * outputMultiplier);
                if (fluidImportInventory.drain(ModHandler.getWater(1), false) == null &&
                    fluidImportInventory.drain(ModHandler.getDistilledWater(1), false) == null) {
                    steamOutput = 0;
                }
            }
            textList.add(new TextComponentTranslation("gregtech.multiblock.fission.steam_output",
                steamOutput, fissionType.baseSteamOutput));
            textList.add(new TextComponentTranslation("gregtech.multiblock.fission.fuel_left",
                fuelBurnTicksLeft * 100/ 45000));

        }
        super.addDisplayText(textList);
    }

    @Override
    protected void updateFormedValid() {
        if (fuelBurnTicksLeft > 0) {
            --this.fuelBurnTicksLeft;
            if (this.currentTemperature < fissionType.maxTemperature && getTimer() % 20 == 0) {
                this.currentTemperature++;
            }
            if (fuelBurnTicksLeft == 0) {
                this.wasActiveAndNeedsUpdate = true;
            }
        } else if (currentTemperature > 0 && getTimer() % 20 == 0) {
            --this.currentTemperature;
        }

        if (currentTemperature >= 100) {
            boolean doWaterDrain = getTimer() % 20 == 0;
            FluidStack drainedWater = fluidImportInventory.drain(ModHandler.getWater(1), doWaterDrain);
            if (drainedWater == null || drainedWater.amount == 0) {
                drainedWater = fluidImportInventory.drain(ModHandler.getDistilledWater(1), doWaterDrain);
            }
            if (drainedWater != null && drainedWater.amount > 0) {
                /*if (currentTemperature > 100 && hasNoWater) {
                    float explosionPower = currentTemperature / 100.0f * 2.0f;
                    getWorld().setBlockToAir(getPos());
                    getWorld().createExplosion(null, getPos().getX() + 0.5, getPos().getY() + 0.5, getPos().getZ() + 0.5,
                        explosionPower, true);
                }*/
                this.hasNoWater = false;
                if (currentTemperature >= 100) {
                    double outputMultiplier = currentTemperature / (fissionType.maxTemperature * 1.0);
                    FluidStack steamStack = ModHandler.getSteam((int) (fissionType.baseSteamOutput * outputMultiplier));
                    steamOutputTank.fill(steamStack, true);
                }
            } else {
                this.hasNoWater = true;
            }
        } else {
            this.hasNoWater = false;
        }

        if (fuelBurnTicksLeft == 0) {
            int fuelMaxBurnTime = setupRecipeAndConsumeInputs();
            if (fuelMaxBurnTime > 0) {
                this.fuelBurnTicksLeft = fuelMaxBurnTime;
                if (wasActiveAndNeedsUpdate) {
                    this.wasActiveAndNeedsUpdate = false;
                } else setActive(true);
                markDirty();
            }
        }

        if (wasActiveAndNeedsUpdate) {
            this.wasActiveAndNeedsUpdate = false;
            setActive(false);
        }
    }

    private int setupRecipeAndConsumeInputs() {
        for (int slotIndex = 0; slotIndex < itemImportInventory.getSlots(); slotIndex++) {
            ItemStack itemStack = itemImportInventory.getStackInSlot(slotIndex);
            boolean valid = new OreIngredient(new UnificationEntry(OrePrefix.ingot, Materials.Uranium235).toString()).apply(itemStack);
            if (valid) {
                if (itemStack.getCount() == 1) {
                    ItemStack containerItem = itemStack.getItem().getContainerItem(itemStack);
                    itemImportInventory.setStackInSlot(slotIndex, containerItem);
                } else {
                    itemStack.shrink(1);
                    itemImportInventory.setStackInSlot(slotIndex, itemStack);
                }
                return 45000;
            }
        }
        return 0;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setInteger("CurrentTemperature", currentTemperature);
        data.setInteger("FuelBurnTicksLeft", fuelBurnTicksLeft);
        data.setBoolean("HasNoWater", hasNoWater);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.currentTemperature = data.getInteger("CurrentTemperature");
        this.fuelBurnTicksLeft = data.getInteger("FuelBurnTicksLeft");
        this.hasNoWater = data.getBoolean("HasNoWater");
        this.isActive = fuelBurnTicksLeft > 0;
    }

    private void setActive(boolean active) {
        this.isActive = active;
        if (!getWorld().isRemote) {
            writeCustomData(100, buf -> buf.writeBoolean(isActive));
            markDirty();
        }
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
        }
    }

    @Override
    protected BlockPattern createStructurePattern() {
        return this.fissionType == null ? null : FactoryBlockPattern.start()
            .aisle("  SSS  ", "  WWW  ", "  SSS  ", "  SSS  ", "  BBB  ", "  XXX  ")
            .aisle(" SSSSS ", " WCCCW ", " SCCCS ", " SCCCS ", " BCCCB ", " XOOOX ")
            .aisle("SSSSSSS", "WCCCCCW", "SCCCCCS", "SCCCCCS", "BCCCCCB", "XOOOOOX")
            .aisle("SSSSSSS", "WCCCCCW", "SCCCCCS", "SCCCCCS", "BCCCCCB", "XOOOOOX")
            .aisle("SSSSSSS", "WCCCCCW", "SCCCCCS", "SCCCCCS", "BCCCCCB", "XOOOOOX")
            .aisle(" SSSSS ", " WCCCW ", " SCCCS ", " SCCCS ", " BCCCB ", " XOOOX ")
            .aisle("  SSS  ", "  WWW  ", "  SSS  ", "  SSS  ", "  BBB  ", "  XFX  ")
            .where('S', statePredicate(this.fissionType.casingState))
            .where('C', statePredicate(this.fissionType.coreState))
            .where('W', statePredicate(this.fissionType.casingState).or(abilityPartPredicate(MultiblockAbility.IMPORT_FLUIDS)))
            .where('B', statePredicate(this.fissionType.casingState).or(abilityPartPredicate(MultiblockAbility.EXPORT_FLUIDS)))
            .where('X', statePredicate(this.fissionType.casingState).or(abilityPartPredicate(MultiblockAbility.IMPORT_ITEMS)))
            .where('O', statePredicate(this.fissionType.controlState))
            .where('F', selfPredicate())
            .where(' ', anyPredicate())
            .build();
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        Textures.FISSION_OVERLAY.render(renderState, translation, pipeline, getFrontFacing(), isActive);
    }

    @Override
    protected boolean checkStructureComponents(List<IMultiblockPart> parts, Map<MultiblockAbility<Object>, List<Object>> abilities) {
        //noinspection SuspiciousMethodCalls
        return abilities.containsKey(MultiblockAbility.IMPORT_FLUIDS)
            && abilities.containsKey(MultiblockAbility.EXPORT_FLUIDS)
            && abilities.containsKey(MultiblockAbility.IMPORT_ITEMS);
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return fissionType.solidCasingRenderer;
    }
}
