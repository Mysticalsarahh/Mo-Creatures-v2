package drzhark.customspawner;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.IRangedAttackMob;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;
import net.minecraftforge.event.Event.Result;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.entity.living.LivingPackSizeEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.terraingen.InitMapGenEvent;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import drzhark.customspawner.entity.EntityData;
import drzhark.customspawner.utils.CMSLog;

public class EventHooks {

    @ForgeSubscribe
    public void peformCustomWorldGenSpawning(PopulateChunkEvent.Populate event)
    {
        int par1 = event.chunkX * 16;
        int par2 = event.chunkZ * 16;
        int x = event.chunkX * 16 + 8 + event.world.rand.nextInt(16);
        int z = event.chunkZ * 16 + 8 + event.world.rand.nextInt(16);

        List customSpawnList = CustomSpawner.INSTANCE.getCustomBiomeSpawnList(event.world.getBiomeGenForCoords(x, z));

        if (customSpawnList != null)
        {
            CustomSpawner.INSTANCE.performWorldGenSpawning(event.world, event.world.getBiomeGenForCoords(x, z), par1 + 8, par2 + 8, 16, 16, event.rand, customSpawnList, CustomSpawner.INSTANCE.worldGenCreatureSpawning);
        }
    }

    @ForgeSubscribe
    public void onLivingPackSize(LivingPackSizeEvent event)
    {
        EntityData entityData = CustomSpawner.classToEntityMapping.get(event.entityLiving.getClass());
        if (entityData != null)
        {
            event.maxPackSize = entityData.getMaxInChunk();
            event.setResult(Result.ALLOW); // needed for changes to take effect
        }
    }

    @ForgeSubscribe
    public void onLivingSpawn(LivingSpawnEvent.CheckSpawn event)
    {
        EntityData entityData = CustomSpawner.classToEntityMapping.get(event.entityLiving.getClass());
        int x = MathHelper.floor_double(event.x);
        int y = MathHelper.floor_double(event.y);
        int z = MathHelper.floor_double(event.z);
        if (entityData != null && !entityData.getCanSpawn())
        {
            if (CustomSpawner.debug) CMSLog.logger.info("Denied spawn for entity " + entityData.getEntityClass() + ". CanSpawn set to false or frequency set to 0!");
                event.setResult(Result.DENY);
        }
        else if ((entityData.getMinSpawnHeight() != -1 && y < entityData.getMinSpawnHeight()) || (entityData.getMaxSpawnHeight() != -1 && y > entityData.getMaxSpawnHeight()))
        {
            if (CustomSpawner.debug) CMSLog.logger.info("Denied spawn for entity " + entityData.getEntityClass() + ". MinY or MaxY exceeded allowed value!");
                event.setResult(Result.DENY);
        }
        /*BiomeGenBase biome = event.world.getBiomeGenForCoords(x, z);
        if (biome != null)
        {
            // handle biome specific spawn settings
        }*/
    }

    // used for Despawner
    @ForgeSubscribe
    public void onLivingDespawn(LivingSpawnEvent.AllowDespawn event)
    {
        if (CustomSpawner.forceDespawns)
        {
            // try to guess what we should ignore
            // Monsters
            if ((IMob.class.isAssignableFrom(event.entityLiving.getClass()) || IRangedAttackMob.class.isAssignableFrom(event.entityLiving.getClass())) || event.entityLiving.isCreatureType(EnumCreatureType.monster, false))
            {
                return;
            }
            // Tameable
            if (event.entityLiving instanceof EntityTameable)
            {
                if (((EntityTameable)event.entityLiving).isTamed())
                {
                    return;
                }
            }
            // Farm animals
            if (event.entityLiving instanceof EntitySheep || event.entityLiving instanceof EntityPig || event.entityLiving instanceof EntityCow || event.entityLiving instanceof EntityChicken)
            {
                // check lightlevel
                if (CustomSpawner.isValidDespawnLightLevel(event.entity, event.world, CustomSpawner.despawnLightLevel))
                {
                    return;
                }
            }
            // Others
            NBTTagCompound nbt = new NBTTagCompound();
            event.entityLiving.writeToNBT(nbt);
            if (nbt != null)
            {
                if (nbt.hasKey("Owner") && !nbt.getString("Owner").equals(""))
                {
                    return; // ignore
                }
                if (nbt.hasKey("Tamed") && nbt.getBoolean("Tamed") == true)
                {
                    return; // ignore
                }
            }
            // despawn check
            EntityPlayer entityplayer = event.world.getClosestPlayerToEntity(event.entityLiving, -1D);
            if (entityplayer != null) //entityliving.canDespawn() && 
            {
                double d = ((Entity) (entityplayer)).posX - event.entityLiving.posX;
                double d1 = ((Entity) (entityplayer)).posY - event.entityLiving.posY;
                double d2 = ((Entity) (entityplayer)).posZ - event.entityLiving.posZ;
                double distance = d * d + d1 * d1 + d2 * d2;
                if (distance > 16384D)
                {
                    event.setResult(Result.ALLOW);
                }
                else if (event.entityLiving.getAge() > 600)
                {
                    if (distance < 1024D)
                    {
                        return;
                    }
                    else
                    {
                        event.setResult(Result.ALLOW);
                    }
                }
            }

            if (event.getResult() == Result.ALLOW && CustomSpawner.debug) 
            {
                int x = MathHelper.floor_double(event.entity.posX);
                int y = MathHelper.floor_double(event.entity.boundingBox.minY);
                int z = MathHelper.floor_double(event.entity.posZ);
                CMSLog.logger.info("Forced Despawn of entity " + event.entityLiving + " at " + x + ", " + y + ", " + z + ". To prevent forced despawns, use /moc forceDespawns false.");
            }
        }
    }

    // this triggers before serverStarting
    /*@ForgeSubscribe
    public void onWorldLoad(WorldEvent.Load event)
    {
        String worldEnvironment = event.world.provider.getClass().getSimpleName().toLowerCase();
        worldEnvironment = worldEnvironment.replace("worldprovider", "");
        worldEnvironment = worldEnvironment.replace("provider", "");
        System.out.println("Environment = " + worldEnvironment);
    }*/

    @ForgeSubscribe
    public void structureMapGen(InitMapGenEvent event)
    {
        String structureClass = event.originalGen.getClass().toString();
        CustomSpawner.structureData.registerStructure(event.type, event.originalGen);
    }
}