/*
 * FILE SYSTEM CHECKER
 */


/**
 *
 * @author Suraj Patel
 * Net ID skp392
 * University ID N16678451
 */

/**
 * Make sure that the folder FS containing the filesystem files i placed in the same folder as this program.
 */


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;
import java.util.Stack;

public class csefsck {
    
    private final String filename = new File("").getAbsolutePath();
    private int freeBlockStart;
    private int freeBlockEnd;
    private int root;
    private boolean[] freeBlockList = new boolean[10000];
    private boolean[] computedFreeBlockList = new boolean[10000];
//    private int currentDir = root;
    private int parentDir [] = new int[10000];       //Array to store parent directory and check later 

    private long now = System.currentTimeMillis()/1000;

    //Check the superblock for the correct Device ID 
    //Check the Creation time is not in future.
    public void chkSuperblock() throws FileNotFoundException
    {
        String superblockFile = filename + "/FS/fusedata.0";
        String line = null;
        String[] field;
        
        FileReader fileReader = new FileReader(superblockFile);
        Scanner sc = new Scanner(fileReader);
        sc.useDelimiter(",");
        while( sc.hasNext())
        {
            line = sc.next();
            field = line.split(":");
            if("devId".equals(field[0].trim()))
            {
                if (!"20".equals(field[1]))         //DeviceID has to be 20
                {
                    System.out.println("Device ID is incorrect!");
                }
                else
                {
                    System.out.println("Device ID is correct!");
                }
            }
            
            if("root".equals(field[0].trim()))
            {
                root = Integer.valueOf(field[1].trim());   
                
                //set parent directory for root in the array as root itself
                parentDir[root] = root;
            }

            if("freeStart".equals(field[0].trim()))
            {
                freeBlockStart = Integer.valueOf(field[1].trim());
            }
            if("freeEnd".equals(field[0].trim()))
            {
                freeBlockEnd = Integer.valueOf(field[1].trim());
            }
            if("{creationTime".equals(field[0]))
            {
                if (now < Long.valueOf(field[1].trim()))
                {
                    System.out.println("Creation Time not consistent");    
                }
            }
        }
        sc.close();
    }

    //Validate that the free block list is accurate
    void chkFreeBlockList() throws FileNotFoundException, IOException
    {
        /*We take two arrays. One of them indicates the block numbers that are mentioned in the 
          free block list as free.
          Then we find which blocks are actually free and set the values in the second array.
          Then on comparing the two array we can see whether the free block list is valid or not.
        */
        
        int i;
        FileReader fileReader;
        Scanner sc;
        java.util.Arrays.fill(freeBlockList, false);
        java.util.Arrays.fill(computedFreeBlockList, true);
        //Make the entries for superblock, free block list and root directory false
        //as we know they are not going to be free.
        for(i=0; i<=root; i++)
        {
            computedFreeBlockList[i] = false;
        }
        for(i=freeBlockStart; i<=freeBlockEnd; i++)
        {
            String freeFile = filename + "/FS/fusedata." + Integer.toString(i);
            fileReader = new FileReader(freeFile);
            sc = new Scanner(fileReader);
            sc.useDelimiter(",");
            
            while(sc.hasNext())
            {
                /*The block numbers mentioned in the freeblocklist are seen and 
                the corresponding index numbers are marked as true in the array*/
                freeBlockList[Integer.valueOf(sc.next().trim())] = true;
            }
        }
        
        //To find actual free blocks, we use DFS algorithm. 
        //If the pointer points to a directory, push the pointer on the stack.
        //If the pointer is that of the file, set all the data blocks of the location to false
        
        Stack st = new Stack();
        st.push(root);
        String stackFile; 
        String line;
        int dirLinkCount = 0;
        String [] fields,content,time, linkCount;
        while(!st.empty())
        {
            int block = (int) st.pop();
            stackFile = filename + "/FS/fusedata." + Integer.toString(block);
            fileReader = new FileReader(stackFile);
            sc = new Scanner(fileReader);
            //sc.useDelimiter(",");
            line = null;
            while(sc.hasNext())
            {
                String temp = sc.next();
                line += temp;
                //Since we are going through every directory inode.
                //We can validate the atime, ctime and mtime of all the directories

                if(temp.contains("atime") || temp.contains("ctime") || temp.contains("mtime"))
                {
                    time = temp.split(":");
                    
                    if(now < Long.valueOf(time[1].trim().substring(0, time[1].trim().length()-1)))
                    {
                        System.out.println(time[0] + " in block no. " + block + " is invalid!");
                    }
                }
                
                if(temp.contains("linkcount"))
                {
                    linkCount = temp.split(":");
                    dirLinkCount = Integer.valueOf(linkCount[1].trim().substring(0, linkCount[1].trim().length()-1));
                }
            }
            int start = line.indexOf("filename_to_inode_dict:{", 20);
            int stop = line.indexOf("}}");
            
            String dirInfo = line.substring(start+24, stop);     //Add 24 to the start index to get exactly the file and directory information

            fields = dirInfo.split(",");
            
            //Check the '.' and '..' entries of the directory inode
            int current =0;      //To check if directory has "."
            int parent = 0;      //To check if the directory has '..'
            int actualLinkCount = 0;   //To check whether link count in the directory matches with its content
            for(i=0; i<fields.length; i++)
            {
                content = fields[i].split(":");
                if("f".equals(content[0].trim()))
                {
                    chkFile(Integer.valueOf(content[2]));
                    computedFreeBlockList[Integer.valueOf(content[2])] = false;
                    actualLinkCount++;
                }
                
                if("d".equals(content[0].trim()))
                {
                    actualLinkCount++;
                    if(!".".equals(content[1].trim()) && !"..".equals(content[1].trim()))
                    {
                        computedFreeBlockList[Integer.valueOf(content[2])] = false;
                        st.push(Integer.valueOf(content[2]));
                        //for this directory the parent would the one recently popped from the stack
                        parentDir[Integer.valueOf(content[2])] = block;
                    }
    
                    if(".".equals(content[1].trim()))
                    {
                        if(block != Integer.valueOf(content[2]))
                        {
                            System.out.println("Block number for '.' in the directory " + block + " is " 
                                               + Integer.valueOf(content[2]) + " which is incorrect");
                            current++;
                        }
                        else
                        {
                            current++;
                        }
                    }
                    
                    if("..".equals(content[1].trim()))
                    {
                        if(parentDir[block] != Integer.valueOf(content[2]))
                        {
                            System.out.println("Block number for '..' in the directory " + block + " is " 
                                                + Integer.valueOf(content[2]) + " which is incorrect." 
                                                + "The actual parent directory is " + parentDir[block]);
                            parent++;
                        }   
                        else
                        {
                            parent++;
                        }
                    }
                }
            }
            if(current != 1 || parent != 1)
            {
                System.out.println("The directory at block " + block + " is missing either '.' or '..' entry");
            }
            
            if(actualLinkCount != dirLinkCount)
            {
                System.out.println("The directory's link count doesn't match the number of links in the filename_to_inode_dict " +
                                   "for block number: " + block);
            }
        }
        
        //Check the free block list and show where there is inconsistency if any.
        for(i=0; i<10000; i++)
        {    
            if(freeBlockList[i] == computedFreeBlockList[i])
            {
                continue;
            }
            else
            {
                if(computedFreeBlockList[i] == true && freeBlockList[i] == false)
                {
                    System.out.println("Block no. " + i + " is free but is not mentioned in the free block list.");
                }
                else
                {
                    System.out.println("Block no. " + i + " is not free but mentioned in the free block list.");
                }
            }
        }    
    }
    
    //Check file contents
    void chkFile(int loc) throws FileNotFoundException, IOException
    {
        String file = filename + "/FS/fusedata." + Integer.toString(loc);
        String line = null;
        String [] time,size;
        int fileSize = 0;
        FileReader fileReader = new FileReader(file);
        Scanner sc = new Scanner(fileReader);
        
        while (sc.hasNext())
        {
            String temp = sc.next();
            line += temp + " ";
            //Since we are going through every directory inode.
            //We can validate the atime, ctime and mtime of all the directories

            if(temp.contains("atime") || temp.contains("ctime") || temp.contains("mtime"))
            {
                time = temp.split(":");

                if(now < Long.valueOf(time[1].trim().substring(0, time[1].trim().length()-1)))
                {
                    System.out.println(time[0] + " in block no. " + loc + " is invalid!");
                }
            }
            
            if(temp.contains("size"))
            {
                size = temp.split(":");
                fileSize = Integer.valueOf(size[1].trim().substring(0, size[1].trim().length()-1));
            }
        }
        
        int start = line.indexOf("indirect");
        int stop = line.indexOf("}");
        String fileInode = line;
        String fileInfo = line.substring(start, stop);
        
        int indirectIndex = fileInfo.indexOf(":");
        int indirect = Character.getNumericValue(fileInfo.charAt(indirectIndex+1));
        int locationIndex = fileInfo.indexOf(":", indirectIndex+1);
        int location = Integer.valueOf(fileInfo.substring(locationIndex+1, fileInfo.length()).trim());
        if(indirect == 0)
        {
            computedFreeBlockList[location] = false;
        }
        else if(indirect == 1)
        {
            computedFreeBlockList[location] = false;
            String indexFile = filename + "/FS/fusedata." + Integer.toString(location);
            fileReader = new FileReader(indexFile);
            sc = new Scanner(fileReader);
            int blockCount = 0;
            int dataBlock = 0;
            while (sc.hasNext())
            {
                line = sc.next();
                dataBlock = Integer.valueOf(line.trim());
                computedFreeBlockList[dataBlock] = false;
                blockCount++;
            }
            
            //If indirect is 1 and number of blocks in index block should be more than 1.
            if(blockCount <2)
            {
                System.out.println("The indirect is 1 for the file at block " + loc + " but data contained at location pointer is not an array");
                
                //Correct the indirect by making the value '0' and change the location from index block to data block
                String replaceString = fileInode.substring(0,start+indirectIndex+1);
                replaceString += "0";
                replaceString += fileInode.substring(start+indirectIndex+2, start+locationIndex+1);
                replaceString += Integer.toString(dataBlock) + "}";
                
                FileWriter fileWriter = new FileWriter(file);
                BufferedWriter bw = new BufferedWriter(fileWriter);
                bw.write(replaceString, 4, replaceString.length() - 4);
                bw.close();
                System.out.println("Indirect error corrected at block " + loc);
                
                //Note that once the indirect error is corrected, the block number which was present as location
                //before the correction, i.e. the index block would be free. The function to add the newly free block 
                //to the free block list is not added in this checker.
            }
            
            if(blockCount != ((fileSize/4096)+1))
            {
                System.out.println("The size mentioned in file inode at block " + loc + " is incorrect");
            }
        }
    }
    
    public static void main(String[] args) throws FileNotFoundException, IOException 
    {
        csefsck chk = new csefsck();
        chk.chkSuperblock();
        chk.chkFreeBlockList();
    }    
}
