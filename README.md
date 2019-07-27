# audio-networking
Reliable, Connection-Oriented point-to-point digital communication library in Java via analog audio (cabled).

Inspired by TCP/IP & Ethernet. 

Transmit directories, files, and aribitrary digital data between two computers at ~`3Kbit/s`. 

#### Physical Setup
* Need 2 computers with analog audio input/output jacks, as well as 2 male-male audio cables.
* Connect the analog audio input of one machine 1 to the output of the machine 2, and vice versa.

**Note:** This project is designed to be used with audio cables. In-air transmission will result very high packet loss rates (if
even usable at all).

## audio-networking Architecture (Bottom up)
### Audio I/O
Real-time audio I/O is done using Java's `javax.sound.sampled` package. Audio format is 44.1Khz, 8-bit, mono, signed PCM, little-endian.
The `RealTimeAudioIO` and `WavFileIO` classes implement the `AudioIO` interface. For testing, the class `WavFileAudioIO` can be 
used to read and write audio to `.wav` files.
## Line Encoding
Line encoding is the process that logical bits (`1`, `0`) are converted into a pattern of analog levels for transmission. In our
case, logical bits need to be converted into a pattern of audio levels.

Manchester encoding is used as the line encoding scheme in `audio-networking`. 

#### Encoding
Encoding works by transmitting logical `1`s as an upward transitions in analog level and logical `0`s as downward transitions.
You can think of this as `XOR`ing logcal bits with a clock signal that alternates high-low in the duration of one logical bit.

```
   +----------------------------------------------------------------------------------------------+
   |                            ---------               ---------       -----------------         |
   |                                    |               |       |       |                         |
   |            Logical bits:       1   |   0       0   |   1   |   0   |   1       1             |
   |                                    |               |       |       |                         |
   |                                    -----------------       ---------                         |
   |                    --------+---------------------------------------------------------------> |
   |                                -----   -----   -----   -----   -----   -----   -----         |
   |                                |   |   |   |   |   |   |   |   |   |   |   |   |             |
   |                   Clock:       |   |   |   |   |   |   |   |   |   |   |   |   |             |
   |                                |   |   |   |   |   |   |   |   |   |   |   |   |             |
   |                            -----   -----   -----   -----   -----   -----   -----             |
   |                    --------+---------------------------------------------------------------> |
   |                                ---------   -----       ---------       -----   -----         |
   |                                |       |   |   |       |       |       |   |   |             |
   |          Encoded signal:       |       |   |   |       |       |       |   |   |             |
   | (convention: IEEE 802.3)       |       |   |   |       |       |       |   |   |             |
   |                            -----       -----   ---------       ---------   -----             |
   +----------------------------------------------------------------------------------------------+                                                                          
```

It has 2 important properties:
1. Continous runs of either `1` or `0` do not produce a steady-state analog signal, due to the guaranteed mid-bit transition. Since the sound cards of most modern computers are designed to
   filter out any direct current (DC) bias, this property is hugely important for `audio-networking`.
2. Removes the need for an auxilliary channel to transmit the clock signal for synchronization between sender and reciever. 
   This dramatically simplifies design, but it does come at the cost of effectively doubling bandwidth (can you see why?). 

Although the table above depicts the Manchester encoded signal as square waves, in reality they are written to`AudioIO` as rounded
square waves in order to reduce their high-frequency component, since a there is significant distortion that comes with writing and reading
high-frequency waveforms that are only a few samples wide. 

The default bit duration is 8 samples. On computers with `44.1KHz` sample rate, this translates to a `44100/8 = 5512.5 bit/s` 
maximum bitrate (still orders of magnitude less than the theoretical maximum bitrate on a 44.1KHz bandwidth, though). 
After accounting for the overhead of frames, and the inter-frame gaps, you can get ~`3Kbit/s`.

Shorter bit durations give higher bitrates but unfortunately also increases audio distortion and the likelyhood of a bit-error in transmission. 
Computers with higher-quality digital-to-analog and analog-to-digital converters may be able to handle shorter bit durations.

#### Decoding
Because Manchester encoding ensures that each logical bit has a transition in it, the reciever can easily synchronize its clock and decode.
The reciever can correctly decode bits if their duration has shifted by less than `3/4` times their original length.
Distortion of the audio signal will affect recieved bit length.

## Framing
At this point we are able to send a stream of logical bits by writing them to audio. 
We can also recieve them, but with with no guarantee of accuracy. 

Packing the raw binary stream into frames allows this binary data to be sent with important metadata, 
and creates a convienient way to add error-checking functionality. The table below lists the frame sections and their respective sizes:
```
+--------------+----------------+--------------------------------------------------------------------------------------+--------------------+-----------------+
| Section      | Preamble + SOF | Header                                                                               | Payload (optional) | Inter-Frame gap |
+--------------+----------------+--------------------------------------------------------------------------------------+--------------------+-----------------+
| Subsection   | Preamble | SoF | source | dest | seq | syn | ack | fin | beg | pad | protocol | pay_length | head_chk | data   | pay_chk   |                 |
+--------------+----------+-----+--------+------+-----+-----+-----+-----+-----+-----+----------+------------+----------+--------+-----------+-----------------+
| Size (bytes) |        8       | 2      | 2    | 1   | 1   |     |     |     |     | 1        | 2          | 4        | N      | 4         | min=2           |
+--------------+----------------+--------+------+-----+-----+-----+-----+-----+-----+----------+------------+----------+--------+-----------+-----------------+
| Size (bits)  | 62       | 2   |        |      | 8   | 1   | 1   | 1   | 1   |     |          |            |          |        |           |                 |
+--------------+----------+-----+--------+------+-----+-----+-----+-----+-----+-----+----------+------------+----------+--------+-----------+-----------------+
```
#### Preamble + Start of frame delimiter
Before Line encoding, the preamble is a string 62 alternating `1` and `0`. The start-of-frame (SoF) delimiter is `11`.
Transmitted from left to right, Preamble + SoF looks like this before line encoding:
```
10101010 10101010 10101010 10101010 10101010 10101010 10101010 10101011
```
This long preamble sequence serves an important function clock synchronization in Ethernet. In `audio-networking`, clock 
synchronization is not nearly as large a problem as it is in Ethernet, and synchronization can be done as soon as the SoF delimiter is read.
However, due to the quirks of the sound card on certain computers, there tends to be a large amount of distortion in the first 
~10-20 samples written after a period of silence. Therefore the 'unnecessarily' long preamble serves as a disposable safety net.

Note that when bit lengths are 8, and sample rate is `44.1KHz`, the preamble will be a `44100/8/2 = 2756.25 Hz` tone after
being Manchester encoded. If you record `audio-networking` during a period of transmission, on playback you will hear the preamble as a 
very short chip. 

#### Header
The header holds information important to higher-level components in `audio-networking`, and will be discussed in detail later.
The `pay_length` field of the header represents the number of bytes in the data payload. 

#### Payload
The payload section is where the 'real' binary data is stored. Payload is optional becasue all frames nessecary for the set-up, maintainence, and tear-down
of connections are header-only. 

#### Checksums
Both the header and the payload have 32-bit checksum fields (`head_chk` and `pay_chk`). 
If either of these checksums are incorrect, the entire frame is discarded. 
Because the entire frame is discarded, it makes more sense to use smaller frames when probability of bit errors occuring in 
transit are high.

The `FrameIO` interface exposes blocking methods for sending and reciving frames. 

## Connections (high-level overview)
Each machine is able to set up as many instances of `Connection` as it wants to via `ConnectionHost`. 
`ConnectionHost` manages all the inbound and outbound frames and distributes the inbound frames to the correct 'owner' Connection based
on source and desination `Address`. 

Connections expose a public interface to send and recieve messages. When a `message` is sent, it is automatically
broken down and sent as multiple frames if nessecary. 
Messages are then pieced together from the frames that a `Connection` recieves.
This entire process is invisible to the end user of the `Connection`.

#### Set Up
Connections are set-up with a three-way handshake in a way very similar to how it's done in TCP. 

#### Tear Down
Connections are terminated by sending a Header-only frame with the `fin` bit set. Once the `ConnectionHost` recieves an `ack` frame, 
the Connection is terminated for good.

#### Acknowledgements and Retransmission
After a `frame` is sent, the `Connection` that sent the frame expects to recieve `frame` with its `ack` bit set. This signifies reciept-of-message
and allows the `Connection` to proceed to send the next `frame` in its outbound queue. In the case that no acknowledgement is recieved
after a predefined `timeout` has elapsed, the last `frame` will be retransmitted. 

#### Frame Order Guarantee
Whenever a `frame` is sent, it has its `seq` field set to the current frame sequence number. `seq` is incremented each time an outgoing
frame is sent and also whenever an incoming `ack` frame matching the expected (current) sequence number is recieved. 
Frames recieved with the wrong `seq` field are ignored, which guarantees `frame` order. It is also possible to cache the last few
frames instead of discarding them in hopes that future frames recieved will restore order - but this is not implemented yet. 

#### Addresses
The source and destination `Address` fields in each frame are 16 bits long, composed of a `host` byte and a `port` byte. `host` should 
be unique to the machine.

#### Ping utility
`ConnectionHost` exposes a simple ping method that tests for the reachability of an arbitrary host. Prints information about
round-trip-time (RTT) and percentage packet loss. 

## File Transfer
The `FileTransferProtocol` class runs on top of a `Connection` and can send and request files and directories with another host.
Of course, reliable delivery of messages is already guaranteed by `Connection`, so the job of `FileTransferProtocol` is relatively easy.

## Future Improvements
1. Make the switch to stereo audio to take advantage of 2 channels of transmission to double bitrate. Can be done:
   * Asynchronously: run independent connections in each channel.
   * Synchronously: alternate bits read/written from lineEncoder between 2 channels - effectively halves the length of each frame.
2. Encrypted Connections
3. Flow and congestion control to connect more than two machines together (will require additional hardware - i.e. n-channel audio mixer)
