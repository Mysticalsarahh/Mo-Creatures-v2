package drzhark.mocreatures.item;

import java.util.List;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.Icon;
import net.minecraft.world.World;
import drzhark.mocreatures.MoCPetData;
import drzhark.mocreatures.MoCTools;
import drzhark.mocreatures.MoCreatures;
import drzhark.mocreatures.entity.IMoCTameable;
import drzhark.mocreatures.entity.MoCEntityAnimal;
import drzhark.mocreatures.entity.IMoCEntity;
import drzhark.mocreatures.network.MoCServerPacketHandler;

public class MoCItemPetAmulet extends MoCItem
{
    private Icon[] icons;
    private int ageCounter;
    private String name;
    private float health;
    private int edad;
    private int creatureType;
    private String spawnClass;
    private String ownerName;
    private int amuletType;
    private boolean adult;
    private int PetId;

    public MoCItemPetAmulet(int i) 
    {
        super(i);
        maxStackSize = 1;
        setHasSubtypes(true);
        ageCounter = 0;
    }

    public MoCItemPetAmulet(int i, int type) 
    {
    	this(i);
    	this.amuletType = type;
    }
    
    @Override
    public ItemStack onItemRightClick(ItemStack itemstack, World worldObj, EntityPlayer entityplayer)
    {
        if (++ageCounter < 2) 
        { 
            return itemstack; 
        }
        
        ageCounter = 0;
        int i = itemstack.getItemDamage();
       
        if (i == 0) //empty fishnet
        {
            return itemstack;
        }

        if (i != 0)
        {

            double dist = 1D;
            double newPosY = entityplayer.posY;
            double newPosX = entityplayer.posX - (dist * Math.cos((MoCTools.realAngle(entityplayer.rotationYaw - 90F)) / 57.29578F));
            double newPosZ = entityplayer.posZ - (dist * Math.sin((MoCTools.realAngle(entityplayer.rotationYaw - 90F)) / 57.29578F));

            ItemStack emptyAmulet = new ItemStack(MoCreatures.fishnet, 1, 0);
            if (this.amuletType == 1)
            {
            	emptyAmulet = new ItemStack(MoCreatures.superAmulet, 1, 0);
            }
            entityplayer.inventory.setInventorySlotContents(entityplayer.inventory.currentItem, emptyAmulet);
            //entityplayer.inventory.setInventorySlotContents(entityplayer.inventory.currentItem, new ItemStack(MoCreatures.fishnet, 1, 0));
            
            if (MoCreatures.isServer())
            {
            	initAndReadNBT(itemstack);
                
                if (spawnClass.isEmpty() || creatureType == 0)
                {
                    return itemstack;
                }
                try
                {
                    EntityLiving tempLiving = MoCTools.spawnListByNameClass(spawnClass, worldObj);
                    if (tempLiving != null && tempLiving instanceof IMoCEntity)
                    {
                        IMoCTameable storedCreature = (IMoCTameable) tempLiving;
                        ((EntityLiving)storedCreature).setPosition(newPosX, newPosY, newPosZ);
                        storedCreature.setType(creatureType);
                        storedCreature.setTamed(true);
                        storedCreature.setName(name);
                        storedCreature.setOwnerPetId(PetId);
                        storedCreature.setOwner(entityplayer.username);
                        ((EntityLiving)storedCreature).setEntityHealth((int)health);
                        storedCreature.setEdad(edad);
                        storedCreature.setAdult(adult);

                        //if the player using the amulet is different than the original owner
                        if (ownerName != "" && !(ownerName.equals(entityplayer.username)) && MoCreatures.instance.mapData != null)
                        {
                            MoCPetData oldOwner = MoCreatures.instance.mapData.getPetData(ownerName);
                            MoCPetData newOwner = MoCreatures.instance.mapData.getPetData(entityplayer.username);
                            EntityPlayer epOwner = worldObj.getPlayerEntityByName(entityplayer.username);
                            int maxCount = MoCreatures.proxy.maxTamed;
                            if (MoCTools.isThisPlayerAnOP(epOwner))
                            {
                                maxCount = MoCreatures.proxy.maxOPTamed;
                            }
                            if (newOwner == null)
                            {
                                if (maxCount > 0 || !MoCreatures.proxy.enableOwnership)
                                {
                                    // create new PetData for new owner
                                    NBTTagCompound petNBT = new NBTTagCompound();
                                    storedCreature.writeEntityToNBT(petNBT);
                                    MoCreatures.instance.mapData.updateOwnerPet((IMoCTameable)storedCreature, petNBT);
                                }
                            }
                            else // add pet to existing pet data
                            {
                                if (newOwner.getTamedList().tagCount() < maxCount || !MoCreatures.proxy.enableOwnership)
                                {
                                    NBTTagCompound petNBT = new NBTTagCompound();
                                    storedCreature.writeEntityToNBT(petNBT);
                                    MoCreatures.instance.mapData.updateOwnerPet((IMoCTameable)storedCreature, petNBT);
                                }
                            }
                            // remove pet entry from old owner
                            if (oldOwner != null)
                            {
                                for (int j = 0; j < oldOwner.getTamedList().tagCount(); j++)
                                {
                                    NBTTagCompound petEntry = (NBTTagCompound)oldOwner.getTamedList().tagAt(j);
                                    if (petEntry.getInteger("PetId") == PetId)
                                    {
                                        // found match, remove
                                        oldOwner.getTamedList().removeTag(j);
                                    }
                                }
                            }
                        }

                        entityplayer.worldObj.spawnEntityInWorld((EntityLiving)storedCreature);
                        MoCServerPacketHandler.sendAppearPacket(((EntityLiving)storedCreature).entityId, worldObj.provider.dimensionId);
                        if ((MoCreatures.proxy.enableOwnership && ownerName.isEmpty()) || name.isEmpty()) 
                        {
                             MoCTools.tameWithName(entityplayer, storedCreature);
                        }
                    }
                }catch (Exception ex) 
                {
                    if (MoCreatures.proxy.debugLogging) MoCreatures.log.warning("Error spawning creature from fishnet/amulet " + ex);
                }
            }
       }
        return itemstack;
    }

    public void readFromNBT(NBTTagCompound nbt)
    {
    	this.PetId = nbt.getInteger("PetId");
        this.creatureType = nbt.getInteger("CreatureType");
        this.health = nbt.getFloat("Health");
        this.edad = nbt.getInteger("Edad");
        this.name = nbt.getString("Name");
        this.spawnClass = nbt.getString("SpawnClass");
        this.adult = nbt.getBoolean("Adult");
        this.ownerName = nbt.getString("OwnerName");
    }

    public void writeToNBT(NBTTagCompound nbt)
    {
    	nbt.setInteger("PetID", this.PetId);
        nbt.setInteger("CreatureType", this.creatureType);
        nbt.setFloat("Health", this.health);
        nbt.setInteger("Edad", this.edad);
        nbt.setString("Name", this.name);
        nbt.setString("SpawnClass", this.spawnClass);
        nbt.setBoolean("Adult", this.adult);
        nbt.setString("OwnerName", this.ownerName);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerIcons(IconRegister par1IconRegister)
    {
        icons = new Icon[4];
        icons[0] = par1IconRegister.registerIcon("mocreatures"+ this.getUnlocalizedName().replaceFirst("item.", ":")); //empty fishnet
        icons[1] = par1IconRegister.registerIcon("mocreatures"+ this.getUnlocalizedName().replaceFirst("item.", ":") + "full"); //fishnet with generic fish
        icons[2] = par1IconRegister.registerIcon("mocreatures"+ this.getUnlocalizedName().replaceFirst("item.", ":")); //empty superamulet
        icons[3] = par1IconRegister.registerIcon("mocreatures"+ this.getUnlocalizedName().replaceFirst("item.", ":") + "full"); //full superamulet
    }

    @SideOnly(Side.CLIENT)
    @Override
    public Icon getIconFromDamage(int par1)
    {
    	if (this.amuletType == 1)
    	{
    		if (par1 < 1)
    		{
    			return icons[2];
    		}
    		return icons[3];
    	}
        
        if (par1 < 1)
        {
            return icons[0];
        }
        return icons[1];
    }
    
    @SideOnly(Side.CLIENT)

    /**
     * allows items to add custom lines of information to the mouseover description
     */
    @Override
    public void addInformation(ItemStack par1ItemStack, EntityPlayer par2EntityPlayer, List par3List, boolean par4)
    {
    	initAndReadNBT(par1ItemStack);
    	if (spawnClass != "") par3List.add(EnumChatFormatting.AQUA + this.spawnClass);
    	if (name != "")	par3List.add(EnumChatFormatting.BLUE + this.name);
    	if (ownerName != "") par3List.add(EnumChatFormatting.DARK_BLUE + "Owned by " + this.ownerName);
    }
    
    private void initAndReadNBT(ItemStack itemstack)
    {
    	if( itemstack.stackTagCompound == null )
        {
            itemstack.setTagCompound(new NBTTagCompound());
        }
        NBTTagCompound nbtcompound = itemstack.stackTagCompound;
        readFromNBT(nbtcompound);
    }
}