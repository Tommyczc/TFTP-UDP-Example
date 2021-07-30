package tftp.udp.server;

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
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TFTPUDPServer extends Thread {
   
    private static final int Server_port =69; //port
    
    private static final byte RRQ = 1;                  //read the file
    private static final byte WRQ = 2;                  //write the file
    private static final byte DAT = 3;                  //file data
    private static final byte ACK = 4;                  //ack
    private static final byte ERROR = 5;                //error
    private static final int PACKET_SIZE = 516;         //512+2+2
    private DatagramSocket datagramSocket = null;       //
    private InetAddress address = null;                 
    private byte[] requestArray;                       //
    private byte[] buf;                                //
    private DatagramPacket outDatagramPacket;          //send
    private DatagramPacket inDatagramPacket; 
    private int srcPort;
    
    public TFTPUDPServer() throws SocketException {
        this("UDPSocketServer");
    }

    public TFTPUDPServer(String name) throws SocketException {
        super(name);
        datagramSocket = new DatagramSocket(Server_port);
    }

    @Override
    public void run(){
        while(true)
            try {
                theList();
                byte[] recvBuf = new byte[PACKET_SIZE];
                inDatagramPacket = new DatagramPacket(recvBuf, PACKET_SIZE);
                try{
                    datagramSocket.setSoTimeout(1000);}
                catch(IOException e){
                    System.err.println("time out..");
                }
                datagramSocket.receive(inDatagramPacket);
                //System.out.println("recived");
                srcPort=inDatagramPacket.getPort();
                address=inDatagramPacket.getAddress();
                
                if(recvBuf[0]==0 && recvBuf[1]==WRQ){
                    //System.out.println("WRQ");
                    byte[] ack={0,0};
                    String fileName="";
                    int i=2;
                    while(recvBuf[i]!=0){
                        fileName+=(char)recvBuf[i];
                        i++;
                    }
                    System.out.println("recieve a write request:"+fileName);
                    if(!fileExist(fileName)){
                        sendAcknowledgment(ack);
                        ByteArrayOutputStream byteOut=receiveFile();
                        writeFile(byteOut,fileName);
                        System.out.println("The file has been store in server dictionary");
                        System.out.println(new File("dictionary/"+fileName).toURI().toString());
                    }
                    else{
                        byte exist=6;
                        System.err.println("the file is exist, error message is sent");
                        sendError(exist);
                    }
                }
                
                else if(recvBuf[0]==0 && recvBuf[1]==RRQ){
                    //System.out.println("RRQ");
                    String fileName="";
                    int i=2;
                    while(recvBuf[i]!=0){
                        fileName+=(char)recvBuf[i];
                        i++;
                    }
                    System.out.println("recieve a read request:"+fileName);
                    if(fileExist(fileName)){
                       byte[] content= findFile(fileName);
                       sendData(content);
                    }
                    else{
                        byte noFound=1;
                        System.err.println("file not found, error message is sent");
                        sendError(noFound);
                    }
                }
            
        } catch (IOException e) {
            System.err.println(e);
            System.out.println("e");
        } catch (Exception ex) {
            //Logger.getLogger(TFTPUDPServer.class.getName()).log(Level.SEVERE, null, ex);
            System.err.println(ex);
            System.out.println("ex");
        }
        //datagramSocket.close();
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
    
    private ByteArrayOutputStream receiveFile() throws Exception{
        ByteArrayOutputStream byteOutOS = new ByteArrayOutputStream();
        int block = 1;
        do {
            buf = new byte[PACKET_SIZE];   //设置数据缓冲区
            inDatagramPacket = new DatagramPacket(buf,buf.length, address,srcPort); 
           
            datagramSocket.setSoTimeout(1000);
            try{
            datagramSocket.receive(inDatagramPacket);}
            catch(SocketTimeoutException ste){
                throw ste;
            }
            //System.out.println("Have recivedAck "+block+" pakage.");   //block最为一个计数器，计算收到的数据包
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
                System.out.println("recived ack "+(int)blockNumber[0]+(int)blockNumber[1]);
                //dos.write(blockNumber);
            }
        } 
        while (!isLastPacket(inDatagramPacket));
        //System.out.println("All file have been recived！！");
        return byteOutOS;
    }
    
    public void sendData(byte[] content) throws IOException, Exception{
        int position=0;
        byte zeroByte = 0;
        byte single = 1;
        byte tenth=0;
            
        int current=0;
        while(current+1!=content.length){
            position=0;
            if(content.length-current-1>512){
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
                    sended[position] = content[current];
                    //System.out.println(position+"  "+current);
                    current++;
                    position++;
                }
                outDatagramPacket = new DatagramPacket(sended,sended.length, address, srcPort); //发到服务器的数据包
                datagramSocket.send(outDatagramPacket);
                    
                System.out.println("sending " +tenth+single+" package.");
                ByteArrayOutputStream byteOut = receiveFile();
                byte[] recivedAck = byteOut.toByteArray();
                if(recivedAck[0]!=tenth || recivedAck[1]!=single){//check ack
                    if(recivedAck[0]==0 && recivedAck[1]==1){//retransmission
                        current=511;
                    }
                    else{
                        int recivedNum=(int)recivedAck[0]*10+(int)recivedAck[1];
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
                int ByteLength = content.length-current+4;//
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
                for (int i = 0; i < content.length-current; i++) {
                    sended[position] = content[current+i];  
                    //System.out.println(i+current);
                    position++;
                }
                current=content.length-1;
                //System.out.println(current+"  "+position);
                outDatagramPacket = new DatagramPacket(sended,sended.length,address, srcPort); //发到服务器的数据包
                datagramSocket.send(outDatagramPacket);
                System.out.println("sending " +tenth+single+" packages.");
                ByteArrayOutputStream byteOut = receiveFile();
                byte[] recivedAck = byteOut.toByteArray();
                     
                if(recivedAck[0]!=tenth || recivedAck[1]!=single){//check ack and perhaps retransmition
                    if(recivedAck[0]==0 && recivedAck[1]==1){
                        current=511;
                    }
                    else{
                        int recivedNum=(int)recivedAck[0]*10+(int)recivedAck[1];
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
    
    private void sendError(byte errorCode){
        String msg="";
        if(errorCode==0){
            msg="Not defined, see error message (if any)."; 
        }
        else if(errorCode==1){
            msg="File not found.";
        }
        else if(errorCode==2){
            msg="Access violation.";
        }
        else if(errorCode==3){
            msg="Disk full or allocation exceeded.";
        }
        else if(errorCode==4){
            msg="Illegal TFTP operation.";
        }
        else if(errorCode==5){
            msg="Unknown transfer ID.";
        }
        else if(errorCode==6){
            msg="File already exists.";
        }
        else if(errorCode==7){
            msg="No such user.";
        }
        byte[] transfer=msg.getBytes();
        int byteLength=4+transfer.length+1;
        byte[] errorArray=new byte[byteLength];
        //byte[] errorArray={0,ERROR,0,errorCode};
        errorArray[0]=0;errorArray[1]=ERROR;errorArray[2]=0;errorArray[3]=errorCode;
        
        for(int i=0; i<transfer.length; i++){
            errorArray[4+i]=transfer[i];
        }
        errorArray[byteLength-1]=0;
        DatagramPacket error = new DatagramPacket(errorArray, errorArray.length,address,srcPort); 
        try {
            datagramSocket.send(error);
        } catch (IOException ex) {
            //Logger.getLogger(TFTPUDPServer.class.getName()).log(Level.SEVERE, null, ex);
            System.err.println(ex);
        }
    }
    
    private void sendAcknowledgment(byte[] blockNumber){
        byte[] ACKArray = { 0, ACK, blockNumber[0], blockNumber[1] };  
        DatagramPacket ack = new DatagramPacket(ACKArray, ACKArray.length,address,srcPort);    //ack pakage
        System.out.println("sending an ack: "+ blockNumber[0]+ blockNumber[1]);
        try {
            datagramSocket.send(ack);
        } catch (IOException e) {
            e.printStackTrace();
        }
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

    private boolean isLastPacket(DatagramPacket datagramPacket) {  //判断是否是最后一个数据包
        if (datagramPacket.getLength() < 512)
            return true;
        else
            return false;
    }

    
    private void reportError() {
        String errorCode = new String(buf, 3, 1);                 //获取差错码
        String errorText = new String(buf, 4,inDatagramPacket.getLength() - 1);  //获取差错信息
        System.err.println("Error: " + errorCode + " " + errorText);
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

    public static void main(String[] args) throws IOException {
        System.out.println("Time Server Started");
         new TFTPUDPServer().start();
        
    }

}