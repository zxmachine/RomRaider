# Reading and writing an ECU (Flash view)

**Reading and comparing are tried on one ECU on a bench; writing is not tried on hardware yet.** Read and
Compare (dry run) ran on a bench 16-bit Denso ECU (`wrx02`, `68HC16Y5`) through an OpenPort 2.0 with a 64-bit J2534
driver: the whole 160 KB read in 57 s without a retry, equal byte for byte to a read by another host, and the
compare listed the blocks that differ from another image. Writing has run only against the simulated ECU in the
unit tests. Use it on a car only if you can recover an ECU yourself by other means.

The Flash view (the third tab of the main window, Ctrl+3) reads and writes an ECU through a J2534 adapter, the way
ECUFlash does. One flash method is supported so far:

| Flash method | Memory model | ECUs | Image |
| --- | --- | --- | --- |
| `wrx02` | `68HC16Y5` | 16-bit Subaru Denso ECUs whose boot loader takes a RAM kernel over the K-line | 160 KB |

The method is chosen by the `flashmethod` and `memmodel` of the ROM's definition (the same names ECUFlash uses).

## What it does

* **Read the ECU into a new image**: enters the boot loader, starts the kernel, reads the whole flash 512 bytes
  at a time (each piece checked against the kernel's CRC-32, three tries each), saves it in the folder for reads
  and logs, and opens it in the editor.
* **Compare (dry run)**: compares the ten 16 KB flash blocks of the ECU with the image by CRC-32 and lists those
  that differ. It writes nothing and needs no switch.
* **Write this image**: writes the blocks that differ and the marker block, then compares again. The image comes
  from the editor's selected ROM ("From the editor": taken again at each Compare and Write, so later edits are
  in) or from a file. The confirmation shows the image's ROM ID, CRC-32 and checksum.

The steps on the wire: OBD pin 9 is grounded; the user turns the ignition off and on; until the boot loader
answers, pin 9 is released for 200 ms and grounded again and a handshake is sent at 9600 baud, about every 1.5 s.
The boot loader starts only if pin 9 is grounded when the ECU powers up: an ignition turned on during one of the
200 ms releases (about one time in seven) starts the program instead, and no handshake is answered. After five
unanswered handshakes the view asks to turn the ignition off and on again, and goes on waiting. The kernel goes up
in one scrambled frame, and then the kernel's own frames are used at its baud rate (the receive buffer is emptied
before each request, and a frame that is not the reply to it, such as a late reply or an echo, is skipped). A
write puts 12 V on pin 11, checks it through the kernel, switches the programming voltage through to the flash,
erases the block that holds the "programmed" marker first, erases and programs each changed block 4 KB at a time
(each 4 KB checked by CRC-32), programs the marker block last, switches the programming voltage off and compares
all blocks again.

**The marker block is written with every write, a deliberate difference from the reference host**, which
writes it only when it differs. The boot loader starts the program whenever the two marker bytes are valid. If
the marker block were left alone and a write stopped in the middle of another block, the next key-on would start
a half-written program. Erasing it first and programming it last means that, while any block is half written,
the boot loader keeps control and waits for a new write. The cost is one more erase and program of that 16 KB
block per write. In practice it nearly always differs anyway, because the checksum word lies in it.

After a session the ECU stays in the kernel. Disconnect the test-mode connector and turn the ignition off and on to
start it normally.

## Safety rules

| Rule | Where |
| --- | --- |
| Writing is off: "Allow writing to the ECU" must be ticked, and it is unticked at every start (never saved). Compare needs no switch | `WritePolicy.checkWritingEnabled` |
| Each write needs the typed confirmation `WRITE <ROM ID>`, asked once the blocks to write are known, showing the image's ROM ID, CRC-32 and checksum | `WritePolicy.checkConfirmation` |
| The image must be 160 KB, with the right checksum, the reset words the boot loader starts and a valid "programmed" marker; checked before anything is sent | `Hc16Y5Layout.problems` |
| A wrong checksum is fixed only on request: Fix checksum in the Flash view, or saving the ROM in the editor with a `wordsum16` checksum module | `Hc16Y5Layout.fixChecksum`, `ChecksumWORDSUM16` |
| The definition's flash method and memory model must be this method's | `WritePolicy.checkDefinition` |
| The battery must hold at least the minimum (12.0 V by default; a minimum that is not a number refuses), checked at the start, again right before the programming voltage goes on, and before each block's erase | `WritePolicy.checkBattery` |
| The ECU must hold the image's ROM ID, unless "Allow another ROM ID" is ticked or the ECU's ROM ID is erased (a recovery) | `WritePolicy.checkRomId` |
| A whole read of the ECU is saved before it is overwritten, unless a read of the same ROM ID and the same content (the CRCs of its ten blocks) was saved in this program run; a second ECU with the same ROM ID gets its own | `Wrx02Session.write`, `BackupStore` |
| The programming voltage must be inside the kernel's window | `WritePolicy.checkProgrammingVoltage` |
| Only blocks that differ are written, plus the marker block, erased first and programmed last; a compare follows | `Wrx02Session.write`, `WritePolicy.writeOrder` |
| Once the programming voltage is on, flash disable and programming voltage off follow whatever happens | `Wrx02Session.write` |
| Erase and program only touch application flash, never the boot loader's block | `WritePolicy.checkWritable` |
| Stop works only where it is safe: while waiting for the boot loader, reading and comparing; never while erasing or programming | `CancelToken` |
| RomRaider does not close (window, Exit, a second start handing over) while the ECU is read or written; it says why | `ShellView.busyReason`, `FlashView` |
| If the program ends anyway (the computer shuts down, Ctrl+C), a shutdown hook asks the session to stop after the command that runs (flash disable, programming voltage off), waits up to 20 s, and then switches every pin off itself | `ActiveSessions` |

Each rule has a unit test in `src/test/java/com/romraider/flash/` (`FlashRulesTest`, `Wrx02SessionTest`,
`ui/FlashUiRulesTest`) or `shell/ViewRegistryTest`.

## If something goes wrong

Every error says what happened, whether anything was written, and what to do next. The log is also kept in
`flash-log-<date>.txt` in the folder for reads and logs.

* **Nothing was written** (no adapter, the boot loader or the kernel does not answer, a refused check): fix the
  cause, turn the ignition off, and try again.
* **A write stopped part way** (an error, a lost connection or power): the ECU's flash is partly written, but the
  boot loader is never touched, so the ECU can be written again. Leave the test-mode connector in and press Write
  again: the kernel may still be running and is tried first; if it does not answer, RomRaider enters the boot
  loader again (turn the ignition off and on when asked). Do not try to start the engine until a write ends with
  "flash correct". The marker block was erased first, so the boot loader will not start a half-written program.
  The whole read taken before the write is in the folder for reads and logs.
* **The program ended during a write** (not through its own window, which refuses to close): the session stops
  after the command that runs, or its pins are switched off from the shutdown hook. If the adapter itself lost
  power or its USB connection, what it does with pin 11 is up to the adapter. In every case: write again as above.

## The kernel file

The flashing kernel is not part of RomRaider. Name your own kernel file under "Kernel file" and choose its profile:

| Profile | Replies | Baud rate | Kernel file |
| --- | --- | --- | --- |
| ECUFlash-style | request code + 0x80 | 39.4 kbaud | the kernel's RAM image, with 0x3941 at offset 2 |
| FastECU-style | request code + 0x40 | 62.5 kbaud | the kernel binary; the signature is put in place on upload |

The file must be at most 4 KB. A kernel of the wrong profile is recognised by its reply code and named in the error.

## Adapters

The adapter list shows the J2534 libraries registered on the computer for ISO 9141. The adapter must be able to
drive pins 9 and 11 (programming voltage, short to ground) and read the battery voltage. The library must match
Java's bitness: a 64-bit Java needs a 64-bit J2534 library.

## Trying the view without a car

The tests' simulated ECU can stand in for an adapter. It is test code and is not in a release build. Build the
tests (`ant unittest`), then start RomRaider with `build/test` on the class path and:

```
-Dromraider.flash.testAdapter=com.romraider.flash.sim.SimulatedAdapterProvider
-Dromraider.flash.sim.image=<a 160 KB image the simulated ECU holds>
-Dromraider.flash.sim.kernel=<the kernel file the upload must carry>
-Dromraider.flash.sim.profile=reply80
```

## Not tried yet

* Writing to a real ECU, and any car: the boot loader entry, the kernel, the baud rates and the read path are
  checked on a bench ECU (above); the programming voltage, erase and program, and the marker block are not.
* The battery check reads the adapter's pin 16, which a car feeds from the battery all the time. A bench that feeds
  pin 16 from the ECU's switched supply reads about 0 V while the ECU is off, and a write is refused below the
  minimum: start with the ECU on, and turn it off and on only when the view asks (as a user does with the ignition).
* Adapters other than one that implements J2534 v04.04 programming voltage on pins 9 and 11.
* Recovery from a real power loss in the middle of a write.
