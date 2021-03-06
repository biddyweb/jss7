/*
 * TeleStax, Open Source Cloud Communications  Copyright 2012.
 * and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.mobicents.ss7.management.console;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.util.List;

import javolution.util.FastList;
import javolution.util.FastSet;

import org.apache.log4j.Logger;
import org.mobicents.protocols.ss7.scheduler.Scheduler;
import org.mobicents.protocols.ss7.scheduler.Task;
import org.mobicents.ss7.management.transceiver.ChannelProvider;
import org.mobicents.ss7.management.transceiver.ChannelSelectionKey;
import org.mobicents.ss7.management.transceiver.ChannelSelector;
import org.mobicents.ss7.management.transceiver.Message;
import org.mobicents.ss7.management.transceiver.MessageFactory;
import org.mobicents.ss7.management.transceiver.ShellChannel;
import org.mobicents.ss7.management.transceiver.ShellServerChannel;

/**
 * @author amit bhayani
 *
 */
public class ShellServer extends Task {
    Logger logger = Logger.getLogger(ShellServer.class);

    public static final String CONNECTED_MESSAGE = "Connected to %s %s %s";

    Version version = Version.instance;

    private ChannelProvider provider;
    private ShellServerChannel serverChannel;
    private ShellChannel channel;
    private ChannelSelector selector;
    private ChannelSelectionKey skey;

    private MessageFactory messageFactory = null;

    private String rxMessage = "";
    private String txMessage = "";

    private volatile boolean started = false;

    private String address;

    private int port;

    private final FastList<ShellExecutor> shellExecutors = new FastList<ShellExecutor>();

    public ShellServer(Scheduler scheduler, List<ShellExecutor> shellExecutors) throws IOException {
        super(scheduler);
        this.shellExecutors.addAll(shellExecutors);
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        logger.info("Starting SS7 management shell environment");
        provider = ChannelProvider.provider();
        serverChannel = provider.openServerChannel();
        InetSocketAddress inetSocketAddress = new InetSocketAddress(address, port);
        serverChannel.bind(inetSocketAddress);

        selector = provider.openSelector();
        skey = serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        messageFactory = ChannelProvider.provider().getMessageFactory();

        this.logger.info(String.format("ShellExecutor listening at %s", inetSocketAddress));

        this.started = true;
        this.activate(false);
        scheduler.submit(this, scheduler.MANAGEMENT_QUEUE);
    }

    public void stop() {
        this.started = false;

        try {
            skey.cancel();
            if (this.channel != null) {
                this.channel.close();
                this.channel = null;
            }
            serverChannel.close();
            selector.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        this.logger.info("Stopped ShellExecutor service");
    }

    public int getQueueNumber() {
        return scheduler.MANAGEMENT_QUEUE;
    }

    public long perform() {
        if (!this.started)
            return 0;

        try {
            FastSet<ChannelSelectionKey> keys = selector.selectNow();

            for (FastSet.Record record = keys.head(), end = keys.tail(); (record = record.getNext()) != end;) {
                ChannelSelectionKey key = (ChannelSelectionKey) keys.valueOf(record);

                if (key.isAcceptable()) {
                    accept();
                } else if (key.isReadable()) {
                    ShellChannel chan = (ShellChannel) key.channel();
                    Message msg = (Message) chan.receive();

                    if (msg != null) {
                        rxMessage = msg.toString();
                        logger.info("received command : " + rxMessage);
                        if (rxMessage.compareTo("disconnect") == 0) {
                            this.txMessage = "Bye";
                            chan.send(messageFactory.createMessage(txMessage));

                        } else {
                            String[] options = rxMessage.split(" ");
                            ShellExecutor shellExecutor = null;
                            for (FastList.Node<ShellExecutor> n = this.shellExecutors.head(), end1 = this.shellExecutors.tail(); (n = n
                                    .getNext()) != end1;) {
                                ShellExecutor value = n.getValue();
                                if (value.handles(options[0])) {
                                    shellExecutor = value;
                                    break;
                                }
                            }

                            if (shellExecutor == null) {
                                logger.warn(String.format("Received command=\"%s\" for which no ShellExecutor is configured ",
                                        rxMessage));
                                chan.send(messageFactory.createMessage("Invalid command"));
                            } else {
                                this.txMessage = shellExecutor.execute(options);
                                chan.send(messageFactory.createMessage(this.txMessage));
                            }

                        } // if (rxMessage.compareTo("disconnect")
                    } // if (msg != null)

                    // TODO Handle message

                    rxMessage = "";

                } else if (key.isWritable() && txMessage.length() > 0) {

                    if (this.txMessage.compareTo("Bye") == 0) {
                        this.closeChannel();
                    }
                    this.txMessage = "";
                }
            }
        } catch (IOException e) {
            logger.error("IO Exception while operating on ChannelSelectionKey. Client CLI connection will be closed now", e);
            try {
                this.closeChannel();
            } catch (IOException e1) {
                logger.error("IO Exception while closing Channel", e);
            }
        } catch (Exception e) {
            logger.error("Exception while operating on ChannelSelectionKey. Client CLI connection will be closed now", e);
            try {
                this.closeChannel();
            } catch (IOException e1) {
                logger.error("IO Exception while closing Channel", e);
            }
        }

        if (this.started)
            scheduler.submit(this, scheduler.MANAGEMENT_QUEUE);

        return 0;
    }

    private void accept() throws IOException {
        ShellChannel channelTmp = serverChannel.accept();

        if (logger.isDebugEnabled()) {
            logger.debug("Accepting client connection. Remote Address= " + channelTmp.getRemoteAddress());
        }

        if (this.channel != null) {
            String exitmessage = "Already client from " + this.channel.getRemoteAddress()
                    + " is connected. Closing this connection";

            logger.warn(exitmessage);

            channelTmp.sendImmediate(messageFactory.createMessage(exitmessage));

            channelTmp.close();
            return;
        }

        this.channel = channelTmp;

        // skey.cancel();
        // skey = this.channel.register(selector, SelectionKey.OP_READ |
        // SelectionKey.OP_WRITE);
        this.channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

        String messageToSend = String.format(CONNECTED_MESSAGE, this.version.getProperty("name"),
                this.version.getProperty("version"), this.version.getProperty("vendor"));
        this.channel.send(messageFactory.createMessage(messageToSend));

        if (logger.isDebugEnabled()) {
            logger.debug("Sent message to remote client= " + messageToSend);
        }

    }

    private void closeChannel() throws IOException {
        if (this.channel != null) {
            try {
                this.channel.close();
            } catch (IOException e) {
                logger.error("Error closing channel", e);
            }

            if (logger.isDebugEnabled()) {
                logger.debug("Closed client connection. Remote Address= " + this.channel.getRemoteAddress());
            }

            this.channel = null;
        }

        // skey.cancel();
        // skey = serverChannel.register(selector, SelectionKey.OP_ACCEPT);
    }
}
