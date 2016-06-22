package serverapp;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Server_app {

    private final int port = 12345;  
    private ServerSocket serverSocket;
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private FileInputStream fileReader;
    private DataInputStream inData;
    private DataOutputStream outData;
    private String[] fileList;
    private final Map<String, Integer> downloadStats = new TreeMap<>();
    private final File serverFolder = new File("C:\\testdir");
    private File[] serverFiles;
    private String command;
    private List<String> log = new ArrayList<>();
    private FileWriter logWriter;
    private FileWriter statsWriter;
    private static BufferedReader reader;

    public static void main(String[] args) {
        reader = new BufferedReader(new InputStreamReader(System.in));
        final Server_app s = new Server_app();
        s.runServer();
        s.fillFileList();
        s.setDownloadCounters();
        //Создание и запуск потока получения команд
        new Thread(new Runnable() {
            @Override
            public void run()
            {
                s.recieveCommands();
            }
        }).start();
        //Создание и запуск потока, принимающего команду остановки сервера
        new Thread(new Runnable() {
            @Override
            public void run() 
            {
                s.serverShutdown();
            }
        }).start();
        //Создание и запуск потока пишущего статистику загрузок
        new Thread(new Runnable() {
            @Override
            public void run() 
            {
                while (true) {                    
                try {
                    Thread.sleep(20000);
                } catch (InterruptedException ex) {
                    s.message(ex.getMessage());
                }
                s.writeStatistics();
                }
            }
        }).start();
    }
    //метод запускает серверный сокет
    public void runServer() {
        try {
            serverSocket = new ServerSocket(port);
            message("Server started");
            message("to stop the server you can use -exit command \n");
        } catch (IOException ex) {
            message(ex.getMessage());
        }
    }
    //метод получает список файлов сервера
    public void fillFileList() {
        if (serverFolder.exists() && serverFolder.isDirectory() && serverFolder.canRead()) {
            fileList = serverFolder.list();
        }
    }
    //метод устанавливает счетчики скачиваний для каждого файла
    public void setDownloadCounters() {
        for (String fileName : fileList) {
            downloadStats.put(fileName, 0);
        }
    }
    //метод увеличивает значение счетчика для каждой загрузки
    public void increaseDownloadCounters(String fileName) {
        for (Map.Entry<String, Integer> entry : downloadStats.entrySet()) {
            if (entry.getKey().equals(fileName)) {
                int tmp = entry.getValue();
                entry.setValue(++tmp);
            }
        }
    }
    //метод получает команды от клиентов
    public synchronized void recieveCommands() {
        try {
            while (true) {
                socket = serverSocket.accept();
                in = socket.getInputStream();
                out = socket.getOutputStream();
                inData = new DataInputStream(in);
                outData = new DataOutputStream(out);              
                command = inData.readUTF();
                if (command.contains(" was disconnected")){
                   outData.writeUTF(command);
                   message(command);
                }
                if (command.equals("-getFilesList")) {
                    message("Recieved command: " + command + " from: " + socket.getInetAddress() + ":" + socket.getPort());
                    sendFileList();
                    message("FileList was sended to " + socket.getInetAddress() + ":" + socket.getPort());
                }
                if (command.contains("-download ")) {
                    boolean b = false;
                    for (String fileName : fileList) {
                        if (command.substring(10).equals(fileName)) {
                            b = true;
                            message("Recieved command: " + command + " from: " + socket.getInetAddress() + ":" + socket.getPort());
                            sendFile();
                            message("File was sended to " + socket.getInetAddress() + ":" + socket.getPort());
                        }
                    }
                    if (b == false) {
                        outData.writeUTF("File not found on server, check filelist");
                        message("File not found on server, check filelist");
                    }
                }
                if (!command.equals("-getFilesList") && !command.contains("-download ") &&!command.contains(" was disconnected")) {
                    outData.writeUTF("Unknown command \"" + command + "\"");
                    message("Unknown command \"" + command + "\"" + " from " + socket.getInetAddress() + ":" + socket.getPort());
                }
            }
        } catch (IOException ex) {
            message(ex.getMessage());
        }
    }
    //метод отправляет клиентам список файлов
    public synchronized void sendFileList() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("FileList:").append(System.getProperty("line.separator"));
        for (String fileName : fileList) {
            sb.append(fileName).append(System.getProperty("line.separator"));
        }
        outData.writeUTF(sb.toString());
    }
    //метод отправляет клиентам файл
    public synchronized void sendFile() throws IOException {
        serverFiles = serverFolder.listFiles();
        for (File serverFile : serverFiles) {
            if (command.substring(10).equals(serverFile.getName())) {
                outData.writeUTF(command);
                fileReader = new FileInputStream(new File(serverFile.getAbsolutePath()));
                int fileSize = (int)serverFile.length();
                outData.writeInt(fileSize);
                byte[] buffer = new byte[fileSize];
                while (fileReader.available() > 0) {
                    fileReader.read(buffer);                   
                }
                outData.write(buffer);
                outData.flush();
                increaseDownloadCounters(serverFile.getName());
            }
        }
    }
    //метод логирует сообщения и события сервера
    public void writeLogFile() {
        try {
            int x = 0;
            logWriter = new FileWriter(serverFolder + "\\server_log.txt");
            for (String logMessage : log) {
                logWriter.write(logMessage);
                logWriter.write(System.getProperty("line.separator"));
                ++x;
                if (x == 2) {
                    logWriter.write(System.getProperty("line.separator"));
                }
            }
            logWriter.close();
        } catch (IOException ex) {
            message(ex.getMessage());
        }
    }
    //метод записывает статистику скачиваний
    public void writeStatistics() {
        try {
            statsWriter = new FileWriter(serverFolder + "\\download_stats.txt");
            for (Map.Entry<String, Integer> entry : downloadStats.entrySet()) {
                statsWriter.write(entry.getKey() + " downloads: " + entry.getValue());
                statsWriter.write(System.getProperty("line.separator"));
            }
            statsWriter.close();
        } catch (IOException ex) {
            message(ex.getMessage());
        }
    }
    //метод позволяет выключить сервер
    public void serverShutdown() {
        try {
            String s = reader.readLine();
            if (s.equals("-exit")) {
                if (in != null) {
                    in.close();
                }
                if (out != null) {
                    out.close();
                }
                if (inData != null) {
                    inData.close();
                }
                if (outData != null) {
                    outData.close();
                }
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
                message("Server shutdown");
                Runtime.getRuntime().exit(0);
            }
        } catch (IOException ex) {
            message(ex.getMessage());
        }
    }
    //метод выводит сообщения на консоль и логирует все события
    public synchronized void message(String msg) {
        System.out.println(msg);
        log.add(msg);
    }
}
