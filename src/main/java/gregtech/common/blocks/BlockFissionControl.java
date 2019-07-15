package gregtech.common.blocks;

import gregtech.common.blocks.BlockFissionControl.FissionControlType;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLiving.SpawnPlacementType;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

public class BlockFissionControl extends VariantBlock<FissionControlType> {
    public BlockFissionControl() {
        super(Material.IRON);
        setUnlocalizedName("fission_control");
        setHardness(5.0f);
        setResistance(10.0f);
        setSoundType(SoundType.METAL);
        setHarvestLevel("wrench", 2);
        setDefaultState(getState(FissionControlType.BWR_FISSION_CONTROL));
    }

    @Override
    public int damageDropped(IBlockState state) {
        return super.getMetaFromState(state);
    }

    @Override
    public boolean canCreatureSpawn(IBlockState state, IBlockAccess world, BlockPos pos, SpawnPlacementType type) {
        return false;
    }

    public enum FissionControlType implements IStringSerializable {

        BWR_FISSION_CONTROL("bwr_fission_control"),
        PWR_FISSION_CONTROL("pwr_fission_control");

        private final String name;

        FissionControlType(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return this.name;
        }

    }

}
