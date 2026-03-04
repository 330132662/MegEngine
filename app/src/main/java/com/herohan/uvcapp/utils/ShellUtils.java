package com.herohan.uvcapp.utils;

import android.util.Log;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

/**
 * @author zuoxianglan
 */
public class ShellUtils {

    private static final String COMMAND_SU = "su";
    private static final String COMMAND_SUKS = "suks";//for EA
    private static final String COMMAND_SH = "sh";
    private static final String COMMAND_LINE_END = "\n";
    private static final String COMMAND_LINE_EXIT = "exit\n";

    private ShellUtils() {

    }

    public static void exeCmd(String command){
        String CMD = "com.megvii.req";
        final String excCommand = CMD + " " + command;
        new Thread(()->{
            try {
                byte[] bytes = excCommand.getBytes(StandardCharsets.US_ASCII);
                InetAddress inetAddress = InetAddress.getByName("127.0.0.1");
                DatagramSocket socket = new DatagramSocket();
                DatagramPacket sendPacket = new DatagramPacket(bytes, bytes.length, inetAddress, 8899);
                socket.send(sendPacket);
                socket.close();
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("ShellUtils","excCommand " + excCommand + ",error:" + e.toString());
            }
        }).start();
    }


    public static CommandResult execute(String command) {
        return execute(new String[]{command});
    }

    public static CommandResult execute(String[] commands) {
        return execute(commands, true, true);
    }

    public static CommandResult execute(String[] commands, boolean isRoot, boolean needResult) {
        int resultCode = -1;
        StringBuilder successBuilder = new StringBuilder();
        StringBuilder failureBuilder = new StringBuilder();
        if (commands != null && commands.length > 0) {
            Process process = null;
            DataOutputStream dos = null;
            BufferedReader successReader = null;
            BufferedReader failureReader = null;

            try {
                final String suCommand = COMMAND_SU;
                process = Runtime.getRuntime().exec(isRoot ? suCommand : COMMAND_SH);
                dos = new DataOutputStream(process.getOutputStream());
                for (String command : commands) {
                    if (command != null) {
                        dos.write(command.getBytes());
                        dos.writeBytes(COMMAND_LINE_END);
                        dos.flush();
                    }
                }
                dos.writeBytes(COMMAND_LINE_EXIT);
                dos.flush();

                resultCode = process.waitFor();

                if (needResult) {
                    successReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    failureReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                    String resultText;
                    while ((resultText = successReader.readLine()) != null) {
                        successBuilder.append(resultText).append(COMMAND_LINE_END);
                    }
                    while ((resultText = failureReader.readLine()) != null) {
                        failureBuilder.append(resultText).append(COMMAND_LINE_END);
                    }
                    final int successLen = successBuilder.length();
                    if (successLen >= COMMAND_LINE_END.length()) {
                        successBuilder.delete(successLen - COMMAND_LINE_END.length(), successLen);
                    }
                    final int failureLen = failureBuilder.length();
                    if (failureLen >= COMMAND_LINE_END.length()) {
                        failureBuilder.delete(failureLen - COMMAND_LINE_END.length(), failureLen);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                close(dos);
                close(successReader);
                close(failureReader);
                if (process != null) {
                    process.destroy();
                }
            }
        }
        return new CommandResult(resultCode, successBuilder.toString(), failureBuilder.toString());
    }

    public static boolean isRoot() {
        return execute(new String[]{}, true, true).isSuccess();
    }


    public static final class CommandResult {

        private final int mErrorCode;

        private final String mSuccessMessage;

        private final String mFailureMessage;

        public CommandResult(int errorCode, String successMessage, String failureMessage) {
            mErrorCode = errorCode;
            mSuccessMessage = successMessage;
            mFailureMessage = failureMessage;
        }

        public int getErrorCode() {
            return mErrorCode;
        }

        public String getSuccessMessage() {
            return mSuccessMessage;
        }

        public String getFailureMessage() {
            return mFailureMessage;
        }

        public boolean isSuccess() {
            return mErrorCode == 0;
        }

        @Override
        public String toString() {
            return "CommandResult{" +
                    "mErrorCode=" + mErrorCode +
                    ", mSuccessMessage='" + mSuccessMessage + '\'' +
                    ", mFailureMessage='" + mFailureMessage + '\'' +
                    '}';
        }
    }

    private static void close(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
