/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tftp.udp.client;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;

/**
 *
 * @author 87066
 */
public class TFTPUDPClient {
    private static final String Server_IP ="127.0.0.1";   //the IP address
    private static final int Server_port =69;            //port
    private static final byte RRQ = 1;                  //read the file
    private static final byte WRQ = 2;                  //write the file
    private static final byte DAT = 3;                  //file data
    private static final byte ACK = 4;                  //ack
    private static final byte ERROR = 5;                //
    private static final int PACKET_SIZE = 516;         //512+2+2
    private DatagramSocket datagramSocket = null;       //
    private InetAddress address = null;                 
    private byte[] requestArray;                       //
    private byte[] buf;                                //
    private DatagramPacket outDatagramPacket;          //send
    private DatagramPacket inDatagramPacket;          //recive
    private Scanner scan;
    
    public TFTPUDPClient(){
    
    }

    public void getConnection() throws Exception {
        address = InetAddress.getByName(Server_IP); //使用InetAddress的静态方法getByName(String host)得到服务器的IP地址    
        System.out.println("Welcome! the ip address is "+address);
        datagramSocket = new DatagramSocket(); 
        scan = new Scanner(System.in);
        
        while(true){
            System.out.println("press 1 to store file, press 2 to retrieve file");
            String option=scan.nextLine();
            if(option.equals("1")){
                theList();
                System.out.println("Please enter file name:");
                String fileName=scan.nextLine();
                System.out.println();
                if(fileExist(fileName)){
                    write(fileName);
                }
                else{System.err.println("file not found");}
            }
            
            else if(option.equals("2")){
                System.out.println("Please enter file name:");
                String fileName=scan.nextLine();
                System.out.println();
                if(!fileExist(fileName)){
                    read(fileName);
                }
                else{
                    System.err.println("the file is exist: " +new File(fileName).toURI().toString());
                }
            }
            
            else{
                System.out.println("Invalid input..");
                getConnection();
            }
        }
    } 
    
    private void theList(){
        System.out.println();
        System.out.println("here are all files in client dictionary:");
        File file = new File("dictionary");
        String[] fileList = file.list();
        for(String fileName:fileList){
            System.out.println(fileName);
        }
        System.out.println();
    }
    
    public void read(String fileName) throws Exception{
        requestArray = createRequest(RRQ, fileName); //通过createRequest(RRQ, fileName)方法得到一个请求读报文
        outDatagramPacket = new DatagramPacket(requestArray,requestArray.length, address, Server_port); //发到服务器的数据包
        datagramSocket.send(outDatagramPacket);    
        ByteArrayOutputStream byteOut = receiveFile(); //利用receiveFile()从服务器接收文件
        System.out.println("the file is saved");
        writeFile(byteOut,fileName);
        System.out.println(new File(fileName).toURI().toString());
        System.out.println();
    }
    
    public void write(String fileName) throws Exception{
        requestArray = createRequest(WRQ, fileName); //createRequest(WRQ, fileName)get a reques 
        outDatagramPacket = new DatagramPacket(requestArray,requestArray.length, address, Server_port); //发到服务器的数据包
        datagramSocket.send(outDatagramPacket);    
        ByteArrayOutputStream byteOut = receiveFile();
        byte[] temp = findFile(fileName);
        byte[] recived = byteOut.toByteArray();
        if(recived[0]==0 && recived[1]==0){
            int position=0;
            byte zeroByte = 0;
            byte single = 1;
            byte tenth=0;
            
            int current=0;
            while(current+1!=temp.length){
                position=0;
                if(temp.length-current-1>512){
                    int ByteLength = 512+4;//
                    byte[] sended=new byte[ByteLength];
                    sended[position]=zeroByte;
                    position++;
                    sended[position]=DAT;
                    position++;
                    sended[position]=tenth;
                    position++;
                    sended[position]=single;
                    position++;
                    for (int i = 0; i < 512; i++) {
                        sended[position] = temp[current];
                        //System.out.println(position+"  "+current);
                        current++;
                        position++;
                    }
                    outDatagramPacket = new DatagramPacket(sended,sended.length, address, Server_port); //发到服务器的数据包
                    datagramSocket.send(outDatagramPacket);
                    
                    System.out.println("sending " +tenth+single+" block1.");
                    byteOut = receiveFile();
                    recived = byteOut.toByteArray();
                    if(recived[0]!=tenth || recived[1]!=single){//check ack
                        if(recived[0]==0 && recived[1]==1){//retransmission
                            current=511;
                        }
                        else{
                            int recivedNum=(int)recived[0]*10+(int)recived[1];
                            current=511+(recivedNum-1)*512;
                        }
                    }
                    
                    if(single==9){
                        single=0;
                        tenth++;
                    }
                    else{
                        single++;
                    }

                }
                
                else{//while the left data is small than 512
                    int ByteLength = temp.length-current+4;//
                    //System.out.println(ByteLength);
                    byte[] sended=new byte[ByteLength];
                    sended[position]=zeroByte;
                    position++;
                    sended[position]=DAT;
                    position++;
                    sended[position]=tenth;
                    position++;
                    sended[position]=single;
                    position++;
                    for (int i = 0; i < temp.length-current; i++) {
                        sended[position] = temp[current+i];  
                        //System.out.println(i+current);
                        position++;
                    }
                    current=temp.length-1;
                    //System.out.println(current+"  "+position);
                    outDatagramPacket = new DatagramPacket(sended,sended.length, address, Server_port); //发到服务器的数据包
                    datagramSocket.send(outDatagramPacket);
                    System.out.println("sending " +tenth+single+" block2.");
                    byteOut = receiveFile();
                    recived = byteOut.toByteArray();
                    
                    
                    if(recived[0]!=tenth || recived[1]!=single){//check ack and perhaps retransmition
                        if(recived[0]==0 && recived[1]==1){
                            current=511;
                        }
                        else{
                            int recivedNum=(int)recived[0]*10+(int)recived[1];
                            current=511+(recivedNum-1)*512;
                        }
                    }
                    
                    
                    if(single==9){
                        single=0;
                        tenth++;
                    }
                    else{
                        single++;
                    }
                }
            }
        }
    }
    
    private byte[] createRequest(final byte opCode, final String fileName) 
    {
        final String mode="octet";
        byte zeroByte = 0;  
        int ByteLength = 2 + fileName.length() + 1 + mode.length() + 1; //
        byte[] ByteArray = new byte[ByteLength];
        int position = 0;      
        ByteArray[position] = zeroByte;
        position++;
        ByteArray[position] = opCode;            //设置操作码（读或写）
        position++;
        for (int i = 0; i < fileName.length(); i++) {
            ByteArray[position] = (byte) fileName.charAt(i);  //返回指定索引处的 char 值,强转为byte类型
            position++;
        }
        ByteArray[position] = zeroByte;       //文件名以0字节作为终止
        position++;
        for (int i = 0; i < mode.length(); i++) {
            ByteArray[position] = (byte) mode.charAt(i);  //返回指定索引处的 char 值,强转为byte类型
            position++;
        }
        ByteArray[position] = zeroByte;       //模式以0字节作为终止
        return ByteArray;
    }
    
    private ByteArrayOutputStream receiveFile() throws Exception{
        ByteArrayOutputStream byteOutOS = new ByteArrayOutputStream();
        int block = 1;
        do {
            buf = new byte[PACKET_SIZE];   //设置数据缓冲区
            inDatagramPacket = new DatagramPacket(buf,buf.length, address,datagramSocket.getLocalPort()); 
            datagramSocket.setSoTimeout(1000);
            try{
                datagramSocket.receive(inDatagramPacket);
            }catch(SocketTimeoutException e){
                System.err.println("The connection is out of time");
            }
            //System.out.println("Have recived "+block+" pakage.");   //block最为一个计数器，计算收到的数据包
            block++;
            byte[] opCode = { buf[0], buf[1] };   //获取接收报文中前两个字节的操作码
            if (opCode[1] == ERROR) {
                reportError();
            } 
            else if (opCode[1] == DAT) {
                byte[] blockNumber = { buf[2], buf[3] };   //get the block num
                DataOutputStream dos = new DataOutputStream(byteOutOS);
                dos.write(inDatagramPacket.getData(), 4,inDatagramPacket.getLength() - 4);
                sendAcknowledgment(blockNumber);    //send ack
            }
            else if(opCode[1] == ACK){
                byte[] blockNumber = { buf[2], buf[3] };
                DataOutputStream dos = new DataOutputStream(byteOutOS);
                dos.write(inDatagramPacket.getData(), 2,inDatagramPacket.getLength());
                System.out.println("recived ack: "+(int)blockNumber[0]+(int)blockNumber[1]);
                //dos.write(blockNumber);
            }
        } 
        while (!isLastPacket(inDatagramPacket));
        System.out.println("All file have been recived！！");
        return byteOutOS;
    }
    
    private void sendAcknowledgment(byte[] blockNumber){
        byte[] ACKArray = { 0, ACK, blockNumber[0], blockNumber[1] };  
        DatagramPacket ack = new DatagramPacket(ACKArray, ACKArray.length,address,inDatagramPacket.getPort());    //ack pakage
        System.out.println("sending an ack: "+ blockNumber[0]+ blockNumber[1]);
        try {
            datagramSocket.send(ack);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    

    private boolean isLastPacket(DatagramPacket datagramPacket) {  //判断是否是最后一个数据包
        if (datagramPacket.getLength() < 512)
            return true;
        else
            return false;
    }

    private void writeFile(ByteArrayOutputStream b, String fileName){
            try {
                OutputStream outputStream = new FileOutputStream("dictionary/"+fileName);
                b.writeTo(outputStream);  // 将此 byte 数组输出流的全部内容写入到指定的输出流参数中
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
    }
    
    private byte[] findFile(String fileName) throws IOException{
        byte[] input=Files.readAllBytes(Paths.get("dictionary/"+fileName));
        //byte[] input=new byte[1024];
        return input;
    }
    
    private boolean fileExist(String fileName){
        File tmpDir = new File("dictionary/"+fileName);
        boolean exists =false;
        if(tmpDir.exists() && tmpDir.isFile()){
            exists=true;
        }
        return exists;
    }
    
    private void reportError() {
        String errorCode = new String(buf, 3, 1);                 //获取差错码
        String errorText = new String(buf, 4,inDatagramPacket.getLength() - 1);  //获取差错信息
        System.err.println("Error: " + errorCode + " " + errorText);
    }
    
    public static void main(String[] args) throws Exception {
        TFTPUDPClient client=new TFTPUDPClient();
        client.getConnection();
        
    }
    
        
    private boolean isLastPacket(byte[] bufferByte) {
        if (bufferByte.length < 512){
            return true;
        }
        else
            return false;
   }
    
}
