package gregtech.common.blocks;

import gregtech.common.blocks.BlockNuclearControlRod.NuclearControlRodType;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLiving.SpawnPlacementType;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

public class BlockNuclearControlRod extends VariantBlock<NuclearControlRodType> {

    public static final PropertyBool ACTIVE = PropertyBool.create("active");

    public BlockNuclearControlRod() {
        super(Material.IRON);
        setUnlocalizedName("nuclear_control_rod");
        setHardness(5.0f);
        setResistance(10.0f);
        setSoundType(SoundType.METAL);
        setHarvestLevel("wrench", 2);
        setDefaultState(getState(NuclearControlRodType.GRAPHITE_CONTROL_ROD).withProperty(ACTIVE, false));
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state.getValue(ACTIVE) ? 15 : 0;
    }

    @Override
    protected BlockStateContainer createBlockState() {
        super.createBlockState();
        return new BlockStateContainer(this, VARIANT, ACTIVE);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return super.getStateFromMeta(meta % 8).withProperty(ACTIVE, meta / 8 >= 1);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return super.getMetaFromState(state) + (state.getValue(ACTIVE) ? 8 : 0);
    }

    @Override
    public int damageDropped(IBlockState state) {
        return super.getMetaFromState(state);
    }

    @Override
    public boolean canCreatureSpawn(IBlockState state, IBlockAccess world, BlockPos pos, SpawnPlacementType type) {
        return false;
    }

    public enum NuclearControlRodType implements IStringSerializable {

        GRAPHITE_CONTROL_ROD("graphite_control_rod"),
        BORON_CARBIDE_CONTROL_ROD("boroncarbide_control_rod");

        private final String name;

        NuclearControlRodType(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return this.name;
        }

    }

}
