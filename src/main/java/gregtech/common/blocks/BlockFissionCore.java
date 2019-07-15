package gregtech.common.blocks;

import gregtech.common.blocks.BlockFissionCore.FissionCoreType;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLiving.SpawnPlacementType;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

public class BlockFissionCore extends VariantBlock<FissionCoreType> {
    public BlockFissionCore() {
        super(Material.IRON);
        setUnlocalizedName("fission_core");
        setHardness(5.0f);
        setResistance(10.0f);
        setSoundType(SoundType.METAL);
        setHarvestLevel("wrench", 2);
        setDefaultState(getState(FissionCoreType.BWR_FISSION_CORE));
    }

    @Override
    public int damageDropped(IBlockState state) { return super.getMetaFromState(state); }

    @Override
    public boolean canCreatureSpawn(IBlockState state, IBlockAccess world, BlockPos pos, SpawnPlacementType type) {
        return false;
    }

    public enum FissionCoreType implements IStringSerializable {

        BWR_FISSION_CORE("bwr_fission_core"),
        PWR_FISSION_CORE("pwr_fission_core");

        private final String name;

        FissionCoreType(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return this.name;
        }

    }

}
