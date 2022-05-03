package com.github.bitstuffing.sshvpn;

import android.util.Log;

import java.util.Arrays;

/**
 * The implementation of an IP packet is designed as a single-element enum to ensure that there
 * is only one instance of the IP packet.
 * <br />
 * <ul>
 * <li>All the TCP_XXX_INDEX positions are relative to the beginning of a TCP header.</li>
 * <li>All the IP_XXX_INDEX positions are relative to the beginning of an IP header.</li>
 * </ul>
 */
enum IPPacket {

    PACKET;
    /**
     * We multiply the number of 32-bit words by 4 to get the number of bytes.
     */
    private static final int BIT_WORD_TO_BYTE_MULTIPLIER = 4;
    private static final int IHL_MASK = 0x0f;
    private static final int TCP_FLAGS_SYN_BIT = 0x02;
    private static final int TCP_FLAGS_ACK_BIT = 0x10;

    private static final int INTEGER_COMPLEMENT = 256;

    /**
     * The index of the octet of the TCP header where the size of the TCP header is stored;
     */
    private static final int TCP_DATA_OFFSET_INDEX = 12;
    /**
     * The index of the octet of the TCP header where the flags, such as SYN, ACK, are stored.
     */
    static final int TCP_FLAGS_INDEX = 13;
    private static final int TRANSPORT_LAYER_DST_PORT_HIGH_BYTE_INDEX = 2;
    private static final int TRANSPORT_LAYER_SPACE_IN_BYTES = 2;
    private static final int TRANSPORT_LAYER_DST_PORT_LOW_BYTE_INDEX = 3;
    private static final int TRANSPORT_LAYER_HEADER_DATA_LENGTH_INDEX = 4;
    
    private static final int UDP_CHECKSUM_1 = 6;
    /**
     * A UDP header length
     */
    private static final int UDP_HEADER_LENGTH = 8;

    /**
     * The number of bytes needed to store an IP address
     */
    private static final int IP_ADDRESS_LENGTH = 4;
    private static final int IP_HEADER_LENGTH_INDEX = 0;
    private static final int IP_TOTAL_LENGTH_HIGH_BYTE_INDEX = 2;
    private static final int IP_TOTAL_LENGTH_LOW_BYTE_INDEX = 3;
    private static final int IP_PROTOCOL_FIELD = 9;
    private static final int IP_CHECKSUM_1 = 10;
    private static final int IP_CHECKSUM_2 = 11;
    private static final int IP_SRC_IP_ADDRESS_INDEX = 12;
    private static final int IP_DST_IP_ADDRESS_INDEX = 16;

    private static final String DOT = ".";

    // Transport-layer protocols
    static final byte TRANSPORT_PROTOCOL_TCP = 6;
    static final byte TRANSPORT_PROTOCOL_UDP = 17;
    
    // IPv4 pseudo header
    private static final int IPV4_PSEUDO_PRE_UDP_PART_LENGTH = 12;
    private static final int IP_PSEUDO_SRC_IP = 0;
    private static final int IP_PSEUDO_DST_IP = 4;
    private static final int IP_PSEUDO_ZEROS = 8;
    private static final int IP_PSEUDO_PROTOCOL = 9;
    // The entity takes two bytes
    private static final int IP_PSEUDO_UDP_LENGTH_FIELD_1 = 10;
    private static final int IP_PSEUDO_UDP_HEADER_START = 12;

    private byte[] mPacket;
    private byte[] mPayload;
    private byte[] mSrcIpAddress;
    private byte[] mDstIpAddress;

    void setPacket(byte[] packet) {
        /*
         * I don't think there is a memory leak; therefore, I don't null out mPacket before
         * assigning a new value.
         */
        mPacket = packet;
        mSrcIpAddress = Arrays.copyOfRange(mPacket, IP_SRC_IP_ADDRESS_INDEX, IP_SRC_IP_ADDRESS_INDEX + IP_ADDRESS_LENGTH);
        mDstIpAddress = Arrays.copyOfRange(mPacket, IP_DST_IP_ADDRESS_INDEX, IP_DST_IP_ADDRESS_INDEX + IP_ADDRESS_LENGTH);
        mPayload = Arrays.copyOfRange(mPacket, getIpHeaderLength() + getTransportLayerHeaderLength(), getTotalLength());
    }

    /**
     * Reset {@link #mPacket} and all the other fields initialized in {@link #setPacket(byte[])}
     * not to see them during the next iteration. Call this method when
     */
    void reset() {
        mPacket = null;
        mPayload = null;
        mSrcIpAddress = null;
        mDstIpAddress = null;
    }

    byte[] getPacket() {
        return mPacket;
    }

    /**
     * It <i>does not</i> return the array size. It returns the size of the IP packet in the
     * array.
     * 
     * @return
     */
    int getTotalLength() {
        int totalLength = convertMultipleBytesToPositiveInt(mPacket[IP_TOTAL_LENGTH_HIGH_BYTE_INDEX],
                mPacket[IP_TOTAL_LENGTH_LOW_BYTE_INDEX]);
        return totalLength;
    }

    byte getProtocol() {
        return mPacket[IP_PROTOCOL_FIELD];
    }

    byte[] getPayload() {
        return mPayload;
    }

    /**
     * 
     * @param headerLengths it's an offset
     * @param payload
     */
    void setPayload(int headerLengths, byte[] payload) {
        mPayload = Arrays.copyOfRange(payload, 0, payload.length);
        System.arraycopy(mPayload, 0, mPacket, headerLengths, mPayload.length);
    }
    
    void setUdpHeaderAndDataLength(int length) {
        int offset = getIpHeaderLength();
        byte []lengthAsArray = convertPositiveIntToBytes(length);
        mPacket[offset + TRANSPORT_LAYER_HEADER_DATA_LENGTH_INDEX] = lengthAsArray[0];
        mPacket[offset + TRANSPORT_LAYER_HEADER_DATA_LENGTH_INDEX + 1] = lengthAsArray[1];
    }

    private static int convertMultipleBytesToPositiveInt(byte... bytes) {
        int value = convertByteToPositiveInt(bytes[0]) << 8;
        value += convertByteToPositiveInt(bytes[1]);
        return value;
    }
    
    static byte[] convertPositiveIntToBytes(int src) {
        byte[] result = new byte[2];
        result[1] = (byte) (255 & src);
        result[0] = (byte) (255 & (src >>> 8));
        return result;
    }

    /**
     * Since byte is a signed type in Java, its positive value cannot be greater than 127.
     * 
     * @param value
     * @return
     */
    private static int convertByteToPositiveInt(byte value) {
        return value >= 0 ? value : value + INTEGER_COMPLEMENT;
    }

    /**
     * Header length in bytes. In this case we don't have to call
     * {@link #convertByteToPositiveInt(byte)} after applying {@link #IHL_MASK} to ensure that
     * a positive number will be multiplied by {@link #BIT_WORD_TO_BYTE_MULTIPLIER}.
     * <br>
     * <br>
     * Consider the following example.
     * <br>
     * <br>
     * 00010110 = 22<br>
     * The ones' complement:<br>
     * 11101001<br>
     * The twos' complement = the ones' complement + 1:<br>
     * 11101001 +<br>
     * 00000001 =<br>
     * 11101010 = -22
     * 
     * <br>
     * <br>
     * So a negative number's high-order bit is always equal to 1, and this bit as well as four
     * high-order bits will always
     * be equal to 0 because the mask we apply in the bitwise AND is equal to 6 = 00000110.<br>
     * 
     * @return the IP header length in bytes
     */
    int getIpHeaderLength() {
        int result = (mPacket[IP_HEADER_LENGTH_INDEX] & IHL_MASK) * BIT_WORD_TO_BYTE_MULTIPLIER;
        return result;
    }

    int getTransportLayerHeaderLength() {
        final int transportLayerHeaderStart = getIpHeaderLength();
        final int protocol = getProtocol();
        switch (protocol) {
        case TRANSPORT_PROTOCOL_TCP:
            final int dataOffsetOctet = transportLayerHeaderStart + TCP_DATA_OFFSET_INDEX;
            /*
             * Why do we shift to the right 4 times? Because the value we are interested in is
             * stored in 4 high-order bits.
             * 
             * Why do we multiply the result by 4? Because the value stored in the 4 high-order
             * bytes
             * is the size of the TCP header in 32-bit words, and we need the result in bytes.
             * 
             * Maksim Dmitriev
             * January 8, 2015
             */
            return (convertByteToPositiveInt(mPacket[dataOffsetOctet]) >>> 4) * 4;
        case TRANSPORT_PROTOCOL_UDP:
            return UDP_HEADER_LENGTH;
        default:
            Log.w("IPPacket", "Unsupported protocol == " + protocol);
            return 0;
        }
    }

    void setTotalLength(int length) {
        byte[] lengthAsBytes = convertPositiveIntToBytes(length);
        mPacket[IP_TOTAL_LENGTH_HIGH_BYTE_INDEX] = lengthAsBytes[0];
        mPacket[IP_TOTAL_LENGTH_LOW_BYTE_INDEX] = lengthAsBytes[1];
    }

    boolean isSyn() {
        return (mPacket[getIpHeaderLength() + TCP_FLAGS_INDEX] & 0xff) == TCP_FLAGS_SYN_BIT;
    }
    
    boolean isAck() {
        return (mPacket[getIpHeaderLength() + TCP_FLAGS_INDEX] & 0xff) == TCP_FLAGS_ACK_BIT;
    }

    /**
     * 
     * @return The IPv4 address in dot-decimal notation
     */
    private String getIpAddressAsAstring(byte[] addressAsBytes) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < addressAsBytes.length; i++) {
            builder.append(convertByteToPositiveInt(addressAsBytes[i]));
            if (i != addressAsBytes.length - 1) {
                builder.append(DOT);
            }
        }
        return builder.toString();
    }

    String getSrcIpAddressAsString() {
        return getIpAddressAsAstring(mSrcIpAddress);
    }

    String getDstIpAddressAsString() {
        return getIpAddressAsAstring(mDstIpAddress);
    }

    byte[] getDstIpAddress() {
        return mDstIpAddress;
    }

    int getDstPort() {
        final int tcpHeaderStart = getIpHeaderLength();
        return convertMultipleBytesToPositiveInt(
                mPacket[tcpHeaderStart + TRANSPORT_LAYER_DST_PORT_HIGH_BYTE_INDEX],
                mPacket[tcpHeaderStart + TRANSPORT_LAYER_DST_PORT_LOW_BYTE_INDEX]);
    }

    void swapPortNumbers() {
        swapRanges(getIpHeaderLength(), TRANSPORT_LAYER_SPACE_IN_BYTES);
    }
    
    void swapIpAddresses() {
        swapRanges(IP_SRC_IP_ADDRESS_INDEX, IP_ADDRESS_LENGTH);
        mSrcIpAddress = Arrays.copyOfRange(mPacket, IP_SRC_IP_ADDRESS_INDEX, IP_SRC_IP_ADDRESS_INDEX + IP_ADDRESS_LENGTH);
        mDstIpAddress = Arrays.copyOfRange(mPacket, IP_DST_IP_ADDRESS_INDEX, IP_DST_IP_ADDRESS_INDEX + IP_ADDRESS_LENGTH);
    }
    
    private void swapRanges(int start, int length) {
        for (int i = 0; i < length; i++) {
            byte temp = 0;
            temp = mPacket[start];
            int secondRangeStart = start + length;
            mPacket[start] = mPacket[secondRangeStart];
            mPacket[secondRangeStart] = temp;
            start++;
            secondRangeStart++;
        }
    }
    
    void calculateIpHeaderCheckSum() {
         byte [] ipv4Header = Arrays.copyOfRange(mPacket, 0, getIpHeaderLength());
         // Assign 0 values to the checksum bytes themselves
         ipv4Header[IP_CHECKSUM_1] = 0;
         ipv4Header[IP_CHECKSUM_2] = 0;

         long checksum = calculateChecksum(ipv4Header);
         
         /*
          * This casting is safe because we only need two low bytes.
          * 
          * Maksim Dmitriev
          * April 14, 2015
          */
         byte []checksumAsArray = convertPositiveIntToBytes((int) checksum);
         System.arraycopy(checksumAsArray, 0, mPacket, IP_CHECKSUM_1, checksumAsArray.length);
    }
    
    void updateUdpCheckSum() {

        byte[] ipv4PseudoHeader = new byte[IPV4_PSEUDO_PRE_UDP_PART_LENGTH + UDP_HEADER_LENGTH + mPayload.length];

        // Fill mIpv4PseudoHeader
        System.arraycopy(mPacket, IP_SRC_IP_ADDRESS_INDEX, ipv4PseudoHeader, IP_PSEUDO_SRC_IP, IP_ADDRESS_LENGTH);
        System.arraycopy(mPacket, IP_DST_IP_ADDRESS_INDEX, ipv4PseudoHeader, IP_PSEUDO_DST_IP, IP_ADDRESS_LENGTH);
        ipv4PseudoHeader[IP_PSEUDO_ZEROS] = 0;
        ipv4PseudoHeader[IP_PSEUDO_PROTOCOL] = TRANSPORT_PROTOCOL_UDP;
        byte []udpLength = new byte[] {
                mPacket[getIpHeaderLength() + TRANSPORT_LAYER_HEADER_DATA_LENGTH_INDEX],
                mPacket[getIpHeaderLength() + TRANSPORT_LAYER_HEADER_DATA_LENGTH_INDEX + 1]
        };
        System.arraycopy(udpLength, 0, ipv4PseudoHeader, IP_PSEUDO_UDP_LENGTH_FIELD_1, udpLength.length);
        // Copy the UDP header itself without the last two bytes which contain the checksum
        System.arraycopy(mPacket, getIpHeaderLength(), ipv4PseudoHeader, IP_PSEUDO_UDP_HEADER_START, UDP_HEADER_LENGTH - 2);
        
        // Data
        System.arraycopy(mPayload, 0, ipv4PseudoHeader, IP_PSEUDO_UDP_HEADER_START + UDP_HEADER_LENGTH, mPayload.length);
        long checksum = calculateChecksum(ipv4PseudoHeader);   
        /*
         * This casting is safe because the value takes two bytes. If it didn't, they would
         * have allocate more space when they were developing the protocol
         * 
         * Maksim Dmitriev
         * May 4, 2015
         */
        byte []checksumAsArray = convertPositiveIntToBytes((int) checksum);
        System.arraycopy(checksumAsArray, 0, mPacket, getIpHeaderLength() + UDP_CHECKSUM_1, checksumAsArray.length);
   }

    /**
     * http://stackoverflow.com/a/4114507/1065835
     * 
     * <br>
     * Calculate the Internet Checksum of a buffer (RFC 1071 -
     * http://www.faqs.org/rfcs/rfc1071.html)
     * Algorithm is
     * 1) apply a 16-bit 1's complement sum over all octets (adjacent 8-bit pairs [A,B], final
     * odd length is [A,0])
     * 2) apply 1's complement to this final sum
     *
     * Notes:
     * 1's complement is bitwise NOT of positive value.
     * Ensure that any carry bits are added back to avoid off-by-one errors
     *
     *
     * @param buf The message
     * @return The checksum
     */
    static long calculateChecksum(byte[] buf) {
        int length = buf.length;
        int i = 0;

        long sum = 0;
        long data;

        // Handle all pairs
        while (length > 1) {
            // Corrected to include @Andy's edits and various comments on Stack Overflow
            data = (((buf[i] << 8) & 0xFF00) | ((buf[i + 1]) & 0xFF));
            sum += data;
            // 1's complement carry bit correction in 16-bits (detecting sign extension)
            if ((sum & 0xFFFF0000) > 0) {
                sum = sum & 0xFFFF;
                sum += 1;
            }

            i += 2;
            length -= 2;
        }

        // Handle remaining byte in odd length buffers
        if (length > 0) {
            // Corrected to include @Andy's edits and various comments on Stack Overflow
            sum += (buf[i] << 8 & 0xFF00);
            // 1's complement carry bit correction in 16-bits (detecting sign extension)
            if ((sum & 0xFFFF0000) > 0) {
                sum = sum & 0xFFFF;
                sum += 1;
            }
        }

        // Final 1's complement value correction to 16-bits
        sum = ~sum;
        sum = sum & 0xFFFF;
        return sum;
    }
}