package drzhark.mocreatures.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet250CustomPayload;
import cpw.mods.fml.common.network.IPacketHandler;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import drzhark.mocreatures.MoCPetData;
import drzhark.mocreatures.MoCTools;
import drzhark.mocreatures.MoCreatures;
import drzhark.mocreatures.entity.IMoCEntity;
import drzhark.mocreatures.entity.IMoCTameable;
import drzhark.mocreatures.entity.passive.MoCEntityHorse;
import drzhark.mocreatures.utils.MoCLog;

public class MoCServerPacketHandler implements IPacketHandler {
    @Override
    public void onPacketData(INetworkManager manager, Packet250CustomPayload packet, Player playerEntity)
    {
        if (packet.channel.equals("MoCreatures"))
        {
            DataInputStream dataStream = new DataInputStream(new ByteArrayInputStream(packet.data));

            try
            {
                int packetID = dataStream.readInt();
                if (packetID == 20) // server receives name info. first packet int: 0 then int entityID and string name
                {
                    int entID = dataStream.readInt();
                    String name = dataStream.readUTF();
                    String ownerName = "";

                    EntityPlayer player = (EntityPlayer) playerEntity;
                    Entity pet = null;
                    List<Entity> entList = player.worldObj.loadedEntityList;

                    for (Entity ent : entList)
                    {
                        if (ent.entityId == entID && ent instanceof IMoCTameable)
                        {
                            ((IMoCEntity) ent).setName(name);
                            ownerName = ((IMoCEntity) ent).getOwnerName();
                            pet = ent;
                            break;
                        }
                    }
                    // update petdata
                    MoCPetData petData = MoCreatures.instance.mapData.getPetData(ownerName);
                    if (petData != null && pet != null && ((IMoCTameable)pet).getOwnerPetId() != -1)
                    {
                        int id = ((IMoCTameable)pet).getOwnerPetId();
                        NBTTagList tag = petData.getPetData().getTagList("TamedList");
                        for (int i = 0; i < tag.tagCount(); i++)
                        {
                            NBTTagCompound nbt = (NBTTagCompound)tag.tagAt(i);
                            if (nbt.getInteger("PetId") == id)
                            {
                                nbt.setString("Name", name);
                                ((IMoCTameable)pet).setName(name);
                            }
                        }
                    }
                }
                //this is the critical one!
                if (packetID == 21) // server receives horse jump command packet. make the horse under this player jump
                {
                    EntityPlayer player = (EntityPlayer) playerEntity;

                    if (player.ridingEntity != null && player.ridingEntity instanceof IMoCEntity)
                    {
                        ((IMoCEntity) player.ridingEntity).makeEntityJump();

                    }
                }

                //already working with workaround
                if (packetID == 22) // server receives insta spawn command packet. int classID, int number
                {
                    EntityPlayer player = (EntityPlayer) playerEntity;
                    int entityId = dataStream.readInt();
                    int number = dataStream.readInt();

                    if ((MoCreatures.proxy.getProxyMode() == 1 && MoCreatures.proxy.allowInstaSpawn) || MoCreatures.proxy.getProxyMode() == 2) // make sure the client has admin rights on server!
                    {
                        MoCTools.spawnNearPlayer(player, entityId, number);
                        if (MoCreatures.proxy.debug) MoCLog.logger.info("Player " + player.username + " used MoC instaspawner and got " + number + " creatures spawned");
                    }
                    else
                    {
                        if (MoCreatures.proxy.debug) MoCLog.logger.info("Player " + player.username + " tried to use MoC instaspawner, but the allowInstaSpawn setting is set to " + MoCreatures.proxy.allowInstaSpawn);
                    }

                }

                //perhaps not needed as client will change the datawatcher type, no animation seen (same effect as with packet)
                if (packetID == 23) // server receives horse transform command packet. set the horse to transform to a specific type
                {
                    int entID = dataStream.readInt();
                    int tType = dataStream.readInt();
                    EntityPlayer player = (EntityPlayer) playerEntity;
                    List<Entity> entList = player.worldObj.loadedEntityList;
                    for (Entity ent : entList)
                    {
                        if (ent.entityId == entID && ent instanceof MoCEntityHorse)
                        {
                            ((MoCEntityHorse) ent).transform(tType);
                            break;
                        }
                    }
                }

                if (packetID == 24) // server receives entity dive command packet.
                {
                    EntityPlayer player = (EntityPlayer) playerEntity;
                    if (player.ridingEntity != null && player.ridingEntity instanceof IMoCEntity)
                    {
                        ((IMoCEntity) player.ridingEntity).makeEntityDive();

                    }
                }
                
                if (packetID == 25) // server receives entity dismount command packet.
                {
                    EntityPlayer player = (EntityPlayer) playerEntity;
                    if (player.ridingEntity != null && player.ridingEntity instanceof IMoCEntity)
                    {
                        ((IMoCEntity) player.ridingEntity).dismountEntity();

                    }
                }
                
                
                if (packetID == 26) // server receives spawn packet with entity name
                {
                    EntityPlayer player = (EntityPlayer) playerEntity;
                    String entityName = dataStream.readUTF();
                    int number = dataStream.readInt();
                    if ((MoCreatures.proxy.getProxyMode() == 1 && MoCreatures.proxy.allowInstaSpawn) || MoCreatures.proxy.getProxyMode() == 2) // make sure the client has admin rights on server!
                    {
                        MoCTools.spawnNearPlayerbyName(player, entityName, number);
                        if (MoCreatures.proxy.debug) MoCLog.logger.info("Player " + player.username + " used MoC instaspawner and got " + number + " creatures spawned");
                    }
                    else
                    {
                        if (MoCreatures.proxy.debug) MoCLog.logger.info("Player " + player.username + " tried to use MoC instaspawner, but the allowInstaSpawn setting is set to " + MoCreatures.proxy.allowInstaSpawn);
                    }
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    //----------------server side
    public static void sendStateInfo(EntityPlayer player, int state)
    {
        // server sending state info. first packet int: 1 then int state
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        try
        {
            data.writeInt(Integer.valueOf(1));
            data.writeInt(Integer.valueOf(state));
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        Packet250CustomPayload packet = new Packet250CustomPayload();
        packet.channel = "MoCreatures";
        packet.data = bytes.toByteArray();
        packet.length = packet.data.length;
        PacketDispatcher.sendPacketToPlayer(packet, (Player) player);
    }

    /**
     * server sends the transform horse command to each client
     * 
     * @param ID
     * @param tType
     *            : transformation type
     */
    public static void sendTransformPacket(int ID, int tType)
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        try
        {
            data.writeInt(Integer.valueOf(2));
            data.writeInt(Integer.valueOf(ID));
            data.writeInt(Integer.valueOf(tType));
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        Packet250CustomPayload packet = new Packet250CustomPayload();
        packet.channel = "MoCreatures";
        packet.data = bytes.toByteArray();
        packet.length = packet.data.length;
    }

    /**
     * server sends the Appear horse command to each client
     * 
     * @param ID
     *            : Entity Horse ID
     * @param dimension
     *            : world dimension (this.worldObj.provider.dimensionId);)
     */
    public static void sendAppearPacket(int ID, int dimension)
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        try
        {
            data.writeInt(Integer.valueOf(3));
            data.writeInt(Integer.valueOf(ID));
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        Packet250CustomPayload packet = new Packet250CustomPayload();
        packet.channel = "MoCreatures";
        packet.data = bytes.toByteArray();
        packet.length = packet.data.length;

        PacketDispatcher.sendPacketToAllInDimension(packet, dimension);
    }

    /**
     * server sends the Appear vanish command to each client
     * 
     * @param ID
     *            : Entity Horse ID
     * @param dimension
     *            : world dimension (this.worldObj.provider.dimensionId);)
     */
    public static void sendVanishPacket(int ID, int dimension)
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        try
        {
            data.writeInt(Integer.valueOf(4));
            data.writeInt(Integer.valueOf(ID));
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        Packet250CustomPayload packet = new Packet250CustomPayload();
        packet.channel = "MoCreatures";
        packet.data = bytes.toByteArray();
        packet.length = packet.data.length;

        PacketDispatcher.sendPacketToAllInDimension(packet, dimension);
    }

    /**
     * server sends the Ogre explode command to each client
     * 
     * @param ID
     *            : Ogre entity ID
     * @param dimension
     *            : world dimension (this.worldObj.provider.dimensionId);)
     */
    public static void sendExplodePacket(int ID, int dimension)
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        try
        {
            data.writeInt(Integer.valueOf(5));
            data.writeInt(Integer.valueOf(ID));
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        Packet250CustomPayload packet = new Packet250CustomPayload();
        packet.channel = "MoCreatures";
        packet.data = bytes.toByteArray();
        packet.length = packet.data.length;

        PacketDispatcher.sendPacketToAllInDimension(packet, dimension);
    }

    /**
     * 
     * @param player
     * @param entityId
     */
    public static void sendNameGUI(EntityPlayerMP player, int entityId)
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        try
        {
            data.writeInt(Integer.valueOf(7));
            data.writeInt(Integer.valueOf(entityId));
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        Packet250CustomPayload packet = new Packet250CustomPayload();
        packet.channel = "MoCreatures";
        packet.data = bytes.toByteArray();
        packet.length = packet.data.length;

        PacketDispatcher.sendPacketToPlayer(packet, (Player) player);
    }

    /**
     * Used to synchronize Zebra shuffling counters/animations
     * 
     * @param ID
     *            : Entity Horse (Zebra) ID
     * @param dimension
     *            : world dimension (this.worldObj.provider.dimensionId);
     */
    public static void sendShufflePacket(int ID, int dimension)
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        try
        {
            data.writeInt(Integer.valueOf(8));
            data.writeInt(Integer.valueOf(ID));
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        Packet250CustomPayload packet = new Packet250CustomPayload();
        packet.channel = "MoCreatures";
        packet.data = bytes.toByteArray();
        packet.length = packet.data.length;

        PacketDispatcher.sendPacketToAllInDimension(packet, dimension);
    }

    /**
     * Stops the shuffle animation in Client Zebras
     * 
     * @param ID
     * @param dimension
     */
    public static void sendStopShuffle(int ID, int dimension)
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        try
        {
            data.writeInt(Integer.valueOf(9));
            data.writeInt(Integer.valueOf(ID));
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        Packet250CustomPayload packet = new Packet250CustomPayload();
        packet.channel = "MoCreatures";
        packet.data = bytes.toByteArray();
        packet.length = packet.data.length;

        PacketDispatcher.sendPacketToAllInDimension(packet, dimension);
    }

    /**
     * Used for heart breeding animation
     * 
     * @param ID
     * @param dimension
     */
    public static void sendHeartPacket(int ID, int dimension)
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        try
        {
            data.writeInt(Integer.valueOf(10));
            data.writeInt(Integer.valueOf(ID));
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        Packet250CustomPayload packet = new Packet250CustomPayload();
        packet.channel = "MoCreatures";
        packet.data = bytes.toByteArray();
        packet.length = packet.data.length;

        PacketDispatcher.sendPacketToAllInDimension(packet, dimension);
    }

    /**
     * Used to synchronize the attack animation
     * 
     * @param ID
     * @param dimension
     */
    public static void sendAnimationPacket(int ID, int dimension, int animationType)
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        try
        {
            data.writeInt(Integer.valueOf(11));
            data.writeInt(Integer.valueOf(ID));
            data.writeInt(Integer.valueOf(animationType));
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        Packet250CustomPayload packet = new Packet250CustomPayload();
        packet.channel = "MoCreatures";
        packet.data = bytes.toByteArray();
        packet.length = packet.data.length;

        PacketDispatcher.sendPacketToAllInDimension(packet, dimension);
    }

    /**
     * Used to sync Entity health from server to client
     * 
     * @param ID
     * @param dimension
     * @param health
     */
    public static void sendHealth(int ID, int dimension, float health)
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        try
        {
            data.writeInt(Integer.valueOf(12));
            data.writeInt(Integer.valueOf(ID));
            data.writeFloat(Float.valueOf(health));
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        Packet250CustomPayload packet = new Packet250CustomPayload();
        packet.channel = "MoCreatures";
        packet.data = bytes.toByteArray();
        packet.length = packet.data.length;

        try
        {
            PacketDispatcher.sendPacketToAllInDimension(packet, dimension);
        }
        catch (Exception e)
        {
            if (MoCreatures.proxy.debug) MoCLog.logger.severe("Exception sending MoCreatures health packet " + e + " to entity ID " + ID);
        }
    }

    public static void sendAttachedEntity(Entity source, Entity target, int dimension)
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        try
        {
            data.writeInt(Integer.valueOf(13));
            data.writeInt(Integer.valueOf(source.entityId));
            data.writeInt(Integer.valueOf(target.entityId));
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        Packet250CustomPayload packet = new Packet250CustomPayload();
        packet.channel = "MoCreatures";
        packet.data = bytes.toByteArray();
        packet.length = packet.data.length;

        PacketDispatcher.sendPacketToAllInDimension(packet, dimension);
    }

    /**
     * Used to synchronize the two bytes between server and client Used for the
     * MoCEntityG
     * 
     * @param ID
     *            = entity ID
     * @param dimension
     *            = worldDimension
     * @param slot
     *            = which slot in the array
     * @param value
     *            = the value to pass
     */
    public static void sendTwoBytes(int ID, int dimension, byte slot, byte value)
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        try
        {
            data.writeInt(Integer.valueOf(14));
            data.writeInt(Integer.valueOf(ID));
            data.writeByte(Byte.valueOf(slot));
            data.writeByte(Byte.valueOf(value));
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        Packet250CustomPayload packet = new Packet250CustomPayload();
        packet.channel = "MoCreatures";
        packet.data = bytes.toByteArray();
        packet.length = packet.data.length;

        PacketDispatcher.sendPacketToAllInDimension(packet, dimension);
    }

    /**
     * Sends message to player
     * 
     * @param player
     * @param message
     */
    public static void sendMsgToPlayer(EntityPlayerMP player, String message)
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        try
        {
            data.writeInt(Integer.valueOf(15));
            data.writeUTF(message);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        Packet250CustomPayload packet = new Packet250CustomPayload();
        packet.channel = "MoCreatures";
        packet.data = bytes.toByteArray();
        packet.length = packet.data.length;

        PacketDispatcher.sendPacketToPlayer(packet, (Player) player);
    }
}