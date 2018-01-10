package antibluequirk.alternatingflux.item;

import blusunrize.immersiveengineering.common.IESaveData;
import blusunrize.immersiveengineering.common.blocks.metal.TileEntityEnergyMeter;
import blusunrize.immersiveengineering.common.blocks.metal.TileEntityRedstoneBreaker;
import blusunrize.immersiveengineering.common.util.ItemNBTHelper;
import blusunrize.immersiveengineering.common.util.Utils;
import blusunrize.immersiveengineering.api.Lib;
import blusunrize.immersiveengineering.api.TargetingInfo;
import blusunrize.immersiveengineering.api.energy.wires.IImmersiveConnectable;
import blusunrize.immersiveengineering.api.energy.wires.IWireCoil;
import blusunrize.immersiveengineering.api.energy.wires.ImmersiveNetHandler;
import blusunrize.immersiveengineering.api.energy.wires.ImmersiveNetHandler.Connection;
import blusunrize.immersiveengineering.api.energy.wires.WireType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import antibluequirk.alternatingflux.block.TileEntityRelayAF;
import antibluequirk.alternatingflux.block.TileEntityTransformerAF;
import antibluequirk.alternatingflux.wire.AFWireType;

public class ItemWireCoil extends ItemAFBase implements IWireCoil {
	public ItemWireCoil() {
		super("wirecoil", 64, "af");
	}

	@Override
	public WireType getWireType(ItemStack stack) {
		switch (stack.getItemDamage()) {
		default:
		case 0:
			return (WireType) AFWireType.AF;
		}
	}

	@Override
	public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
		if (stack.getTagCompound() != null && stack.getTagCompound().hasKey("linkingPos")) {
			int[] link = stack.getTagCompound().getIntArray("linkingPos");
			if (link != null && link.length > 3) {
				tooltip.add(I18n.format(Lib.DESC_INFO + "attachedToDim", link[1], link[2], link[3], link[0]));
			}
		}
	}

	// Copied from IE, due to a lack of other options. See inside the function for details.
	@Nonnull
	@Override
	public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
		TileEntity tileEntity = world.getTileEntity(pos);
		if (tileEntity instanceof IImmersiveConnectable && ((IImmersiveConnectable) tileEntity).canConnect()) {
			ItemStack stack = player.getHeldItem(hand);
			TargetingInfo target = new TargetingInfo(side, hitX, hitY, hitZ);
			WireType wire = getWireType(stack);
			BlockPos masterPos = ((IImmersiveConnectable) tileEntity).getConnectionMaster(wire, target);
			tileEntity = world.getTileEntity(masterPos);
			if (!(tileEntity instanceof IImmersiveConnectable) || !((IImmersiveConnectable) tileEntity).canConnect())
				return EnumActionResult.PASS;

			//If there was some sort of call out here in the original function, we might not need to copy this function.
			if (!((IImmersiveConnectable) tileEntity).canConnectCable(wire, target) || !canConnectCable(wire, tileEntity)) {
				if (!world.isRemote)
					player.sendMessage(new TextComponentTranslation(Lib.CHAT_WARN + "wrongCable"));
				return EnumActionResult.FAIL;
			}

			if (!world.isRemote)
				if (!ItemNBTHelper.hasKey(stack, "linkingPos")) {
					ItemNBTHelper.setIntArray(stack, "linkingPos", new int[] { world.provider.getDimension(),
							masterPos.getX(), masterPos.getY(), masterPos.getZ() });
					NBTTagCompound targetNbt = new NBTTagCompound();
					target.writeToNBT(targetNbt);
					ItemNBTHelper.setTagCompound(stack, "targettingInfo", targetNbt);
				} else {
					WireType type = getWireType(stack);
					int[] array = ItemNBTHelper.getIntArray(stack, "linkingPos");
					BlockPos linkPos = new BlockPos(array[1], array[2], array[3]);
					TileEntity tileEntityLinkingPos = world.getTileEntity(linkPos);
					int distanceSq = (int) Math.ceil(linkPos.distanceSq(masterPos));
					if (array[0] != world.provider.getDimension())
						player.sendMessage(new TextComponentTranslation(Lib.CHAT_WARN + "wrongDimension"));
					else if (linkPos.equals(masterPos))
						player.sendMessage(new TextComponentTranslation(Lib.CHAT_WARN + "sameConnection"));
					else if (distanceSq > (type.getMaxLength() * type.getMaxLength()))
						player.sendMessage(new TextComponentTranslation(Lib.CHAT_WARN + "tooFar"));
					else {
						TargetingInfo targetLink = TargetingInfo
								.readFromNBT(ItemNBTHelper.getTagCompound(stack, "targettingInfo"));
						//If there was some sort of call out here in the original function, we might not need to copy this function.
						if (!(tileEntityLinkingPos instanceof IImmersiveConnectable) || !canConnectCable(wire, tileEntityLinkingPos)
								|| !((IImmersiveConnectable) tileEntityLinkingPos).canConnectCable(wire, targetLink))
							player.sendMessage(new TextComponentTranslation(Lib.CHAT_WARN + "invalidPoint"));
						else {
							IImmersiveConnectable nodeHere = (IImmersiveConnectable) tileEntity;
							IImmersiveConnectable nodeLink = (IImmersiveConnectable) tileEntityLinkingPos;
							boolean connectionExists = false;
							Set<Connection> outputs = ImmersiveNetHandler.INSTANCE.getConnections(world,
									Utils.toCC(nodeHere));
							if (outputs != null)
								for (Connection con : outputs) {
									if (con.end.equals(Utils.toCC(nodeLink)))
										connectionExists = true;
								}
							if (connectionExists)
								player.sendMessage(new TextComponentTranslation(Lib.CHAT_WARN + "connectionExists"));
							else {
								Vec3d rtOff0 = nodeHere.getRaytraceOffset(nodeLink).addVector(masterPos.getX(),
										masterPos.getY(), masterPos.getZ());
								Vec3d rtOff1 = nodeLink.getRaytraceOffset(nodeHere).addVector(linkPos.getX(),
										linkPos.getY(), linkPos.getZ());
								Set<BlockPos> ignore = new HashSet<>();
								ignore.addAll(nodeHere.getIgnored(nodeLink));
								ignore.addAll(nodeLink.getIgnored(nodeHere));
								boolean canSee = Utils.rayTraceForFirst(rtOff0, rtOff1, world, ignore) == null;
								if (canSee) {
									ImmersiveNetHandler.INSTANCE.addConnection(world, Utils.toCC(nodeHere),
											Utils.toCC(nodeLink), (int) Math.sqrt(distanceSq), type);

									nodeHere.connectCable(type, target, nodeLink);
									nodeLink.connectCable(type, targetLink, nodeHere);
									IESaveData.setDirty(world.provider.getDimension());
									Utils.unlockIEAdvancement(player, "main/connect_wire");

									if (!player.capabilities.isCreativeMode)
										stack.shrink(1);
									((TileEntity) nodeHere).markDirty();
									world.addBlockEvent(masterPos, ((TileEntity) nodeHere).getBlockType(), -1, 0);
									IBlockState state = world.getBlockState(masterPos);
									world.notifyBlockUpdate(masterPos, state, state, 3);
									((TileEntity) nodeLink).markDirty();
									world.addBlockEvent(linkPos, ((TileEntity) nodeLink).getBlockType(), -1, 0);
									state = world.getBlockState(linkPos);
									world.notifyBlockUpdate(linkPos, state, state, 3);
								} else
									player.sendMessage(new TextComponentTranslation(Lib.CHAT_WARN + "cantSee"));
							}
						}
					}
					ItemNBTHelper.remove(stack, "linkingPos");
					ItemNBTHelper.remove(stack, "targettingInfo");
				}
			return EnumActionResult.SUCCESS;
		}
		return EnumActionResult.PASS;
	}

	public boolean canConnectCable(WireType wire, TileEntity targetEntity) {
		//We specifically only support whitelisted TEs here.
		//Without this, you can connect the AF wire to any connectable block that doesn't specifically deny it.
		if (!(targetEntity instanceof TileEntityRelayAF) &&
			!(targetEntity instanceof TileEntityTransformerAF) &&
			!(targetEntity instanceof TileEntityRedstoneBreaker) &&
			!(targetEntity instanceof TileEntityEnergyMeter))
			return false;
		return true;
	}
}