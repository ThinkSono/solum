package me.clarius.sdk.solum.example;

import android.bluetooth.BluetoothGatt;

import java.util.LinkedList;
import java.util.Queue;

public class BluetoothOperator {
    interface Command {
        void run();
    }

    public BluetoothOperator() {
    }

    private final Queue<Command> commandQueue = new LinkedList<>();
    private Boolean commandRunning = false;

    public synchronized void addCommand(Command command) {
        commandQueue.add(command);
        this.runNext();
    }

    public synchronized void commandFinished() {
        commandRunning = false;
        this.runNext();
    }

    public synchronized void runNext() {
        if (commandRunning) {
            return;
        }

        Command c = commandQueue.poll();
        if (c == null) {
            return;
        }
        commandRunning = true;
        c.run();
    }

    public synchronized void clear() {
        commandQueue.clear();
        commandRunning = false;
    }
}
