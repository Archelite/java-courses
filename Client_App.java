package clientapp;

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
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Client_App {

    private String strAddress = "127.0.0.1";
    private int port = 12345;
    private InputStream in;
    private OutputStream out;
    private DataInputStream inData;
    private DataOutputStream outData;
    private InetAddress ipAddress;
    private Socket socket;
    private String response;
    private FileOutputStream fileWriter;
    private final File outputDir = new File("C:\\downloads");
    private String[] recievedFileName;
    private List<String> log = new ArrayList<>();
    private FileWriter logWriter;
    private static BufferedReader reader;
    private String command;
    private int fileSize;

    byte[] buffer;

    public static void main(String[] args) {
        final Client_App client_App = new Client_App();
        reader = new BufferedReader(new InputStreamReader(System.in));
        client_App.runClient();
        //Создание и запуск потока отсылающего и принимающего команды
        new Thread(new Runnable() {
            @Override
            public void run() {
                client_App.request_response();
            }
        }).start();
        //Создание и запуск потока пишущего логи
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(20000);
                    } catch (InterruptedException ex) {
                        client_App.message(ex.getMessage());
                    }
                    client_App.writeLogFile();
                }
            }
        }).start();
    }
    //метод выводит сообщение о старте клиента и доступных командах
    private void runClient() {
        message("Client started");
        message("You can enter the following commands: ");
        message("-getFilesList");
        message("-download %file_name.file_extension%");
        message("-exit \n ");
    }
    //метод инициализирует соединение с сервером
    public void init() throws IOException {
        ipAddress = InetAddress.getByName(strAddress);
        socket = new Socket(ipAddress, port);
        in = socket.getInputStream();
        out = socket.getOutputStream();
        inData = new DataInputStream(in);
        outData = new DataOutputStream(out);
    }
    //метод передает команды на сервер и получает ответ для дальнейшей обработки
    private void request_response() {
        while (true) {
            try {
                command = reader.readLine();
                if (command.equals("-exit")) {
                    init();
                    outData.writeUTF("Client " + socket.getLocalAddress() + ":" + socket.getLocalPort() + " was disconnected");
                    in.close();
                    out.close();
                    inData.close();
                    outData.close();
                    socket.close();
                    message("Client shutdown");
                    Runtime.getRuntime().exit(0);
                }
                if (command.equals("-getFilesList")) {
                    init();
                    outData.writeUTF(command);
                    response = inData.readUTF();
                    recieveResponse();
                }
                if (command.contains("-download ")) {
                    init();
                    outData.writeUTF(command);
                    response = inData.readUTF();
                    if (response.equals("File not found on server, check filelist")) {
                        message(response);
                    } else {
                        fileSize = inData.readInt();
                        if (fileSize == 0) {
                            message(response);
                        }
                        if (fileSize > 0) {
                            buffer = new byte[fileSize];
                            while (inData.available() > 0) {
                                inData.read(buffer);
                            }
                            recieveResponse();
                        }
                    }
                }
                if (!command.equals("-getFilesList") && !command.contains("-download ") && !command.equals("-exit")) {
                    init();
                    outData.writeUTF(command);
                    response = inData.readUTF();
                    message(response);
                }
            } catch (IOException ex) {
                message(ex.getMessage());
            }
        }
    }
    //метод принимает ответ от сервера, содержащий список файлов, либо файл
    private void recieveResponse() {
        if (response.contains("FileList:")) {
            recieveFileList();
        }
        if (response.contains("-download ")) {
            recievedFileName = response.split("-download ");
            recieveFile(recievedFileName[1]);
        }
    }
    //метод выводит в консоль список файлов
    private void recieveFileList() {
        message(response);
    }
    //метод отправляет файл в существующую директорию, либо создает новую
    private void recieveFile(String recievedfileName) {
        if (outputDir.exists() && outputDir.isDirectory() && outputDir.canWrite()) {
            writeDownloadedFile(recievedfileName);
            message(recievedfileName + " was downloaded in existing directory");
        } else if (!outputDir.exists()) {
            outputDir.mkdir();
            writeDownloadedFile(recievedfileName);
            message(recievedfileName + " was downloaded in new directory");
        }
    }
    //метод сохраняет локально загруженный файл
    private void writeDownloadedFile(String recievedfileName) {
        try {
            fileWriter = new FileOutputStream(new File(outputDir + "\\" + recievedfileName));
            fileWriter.write(buffer);
            fileWriter.close();
        } catch (IOException ex) {
            message(ex.getMessage());
        }
    }
    //метод выводит сообщения на консоль и логирует все события
    private void message(String msg) {
        System.out.println(msg);
        log.add(msg);
    }
    //метод записывает сообщения в лог-файл
    private void writeLogFile() {
        try {
            int x = 0;
            if (!outputDir.exists()) {
                outputDir.mkdir();
            }
            logWriter = new FileWriter(outputDir + "\\client_log.txt");
            for (String logMessage : log) {
                logWriter.write(logMessage);
                logWriter.write(System.getProperty("line.separator"));
                ++x;
                if (x == 5) {
                    logWriter.write(System.getProperty("line.separator"));
                }
            }
            logWriter.close();
        } catch (IOException ex) {
            message(ex.getMessage());
        }
    }
}
