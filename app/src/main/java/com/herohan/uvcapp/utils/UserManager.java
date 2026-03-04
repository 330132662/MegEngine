package com.herohan.uvcapp.utils;
import android.os.Build;

import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.io.*;
import java.io.Serializable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


public class UserManager {
    private static HashMap<Integer, User> UserMap = new HashMap<>();
    private static int lastId = 0;
    private static final int MAX_USER_CNTS =  40001;
    private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    private final Lock r = rwl.readLock();
    private final Lock w = rwl.writeLock();

    public static void setUser_list(byte[] user_list) {
        UserManager.user_list = user_list;
    }

    private static byte[] user_list = new byte[MAX_USER_CNTS];
/*
    public  byte[] loadUpgradeFile(String filePath) throws IOException {
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(filePath))) {
            return  (byte[]) in.readObject();
        } catch (FileNotFoundException | ClassNotFoundException e) {
            System.out.println("found't upgrade file:" + filePath);
            return new byte[0];
        }
    }
*/
public static byte[] loadUpgradeFile(String filePath) {
    File file = new File(filePath);
    byte[] fileContent = new byte[(int) file.length()];

    try (FileInputStream fileInputStream = new FileInputStream(file)) {
        // 读取文件内容到byte数组
        fileInputStream.read(fileContent);
    } catch (IOException e) {
        e.printStackTrace();
    }

    return fileContent;
}
    public int loadBook(String filePath){
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(filePath))) {
            UserMap = (HashMap<Integer, User>) in.readObject();
            Iterator<Map.Entry<Integer, User>> iterator = UserMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Integer, User> entry = iterator.next();
                user_list[entry.getKey()] = 1;
            }
            return 0;
        } catch (FileNotFoundException e) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(Paths.get(filePath)))) {
                    out.writeObject(UserMap);
                    return 0;
                } catch (IOException ee) {
                    ee.printStackTrace();
                }
            }
            //e.printStackTrace();
            return -1;
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public int saveBook(String filePath) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(Paths.get(filePath)))) {
                out.writeObject(UserMap);
                System.out.println("book save successfully.");
                return 0;
            } catch (IOException e) {
                e.printStackTrace();
                return -1;
            }
        }
        return 0;
    }

    public  int addUser(int face_id,int type,String name,byte[] feature) {
        w.lock();
        User user = new User(face_id, name, type,feature);
        UserMap.put(face_id, user);
        System.out.println("User added successfully.");
        user_list[face_id] = 1;
        w.unlock();
        return 1;
    }

    public  void removeUser(int face_id) {
        w.lock();
        if (UserMap.remove(face_id) != null) {
            System.out.println("User removed successfully.");
            user_list[face_id] = 0;
        } else {
            System.out.println("User not found.");
        }
        w.unlock();
    }

    public  void removeAllUser()
    {
        w.lock();
        UserMap.clear();
        Arrays.fill(user_list,(byte) 0);
        w.unlock();
    }

    public  void updateUser(int face_id,int type,String name,byte[] feature) {
        w.lock();
        User user = UserMap.get(face_id);
        if (user != null) {
            user.setName(name);
            user.setType(type);
            user.setFeature(feature);
            System.out.println("User updated successfully.");
        } else {
            System.out.println("User not found.");
        }
        w.unlock();
    }

    public int getUserCnts() {
        int cnts = 0;
        r.lock();
        cnts = UserMap.size();
        r.unlock();
        return cnts;
    }

    public int getUserCnts(int type)
    {
        int cnts = 0;
        r.lock();
        Iterator<Map.Entry<Integer, User>> iterator = UserMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, User> entry = iterator.next();
            //System.out.println("key_id = " + entry.getKey() + ", face_id = " + entry.getValue().face_id);
            if(entry.getValue().type == type)
            {
                cnts ++;
            }
        }
        r.unlock();
        return cnts;
    }

    public UserData getUserInfo(int face_id){
        r.lock();
        User user = UserMap.get(face_id);
        UserData data = new UserData();
        if(user == null) {
            data.type = -1;
            data.name = "";
        } else {
            data.type = user.type;
            data.name = user.name;
            data.ft = Arrays.copyOf(user.ft, user.ft.length);
        }
        r.unlock();
        return data;
    }

    public  int getLastId() {
        r.lock();
        for(int i = 1; i <= user_list.length; i++)
        {
            if(user_list[i] == 0)
            {
                lastId = i;
                r.unlock();
                return lastId;
            }
        }
        r.unlock();
        return 0;
    }

    public static class UserData{
        public  String name;
        public  byte ft[];
        public  int type;
    }

    static class User  implements Serializable{
        private static final long serialVersionUID = 1L; // 序列化版本控制

        private int face_id;
        private String name;
        private byte ft[];
        private int type;  /*0 face 1 plam*/

        public User(int id, String name, int type, byte[] ft) {
            this.face_id = id;
            this.name = name;
            this.type = type;
            this.ft = Arrays.copyOf(ft,ft.length);
        }

        public int getId() {
            return face_id;
        }

        public void setId(int id) {
            this.face_id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getType(){
            return type;
        }

        public void setType(int type){
            this.type = type;
        }

        public byte[] getFeature(){
            return this.ft;
        }

        public void setFeature(byte[] ft){
            this.ft = ft;
        }
    }
}
