import java.io.File;
import java.util.HashMap;
import java.util.Scanner;

import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.chat2.ChatManager;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smackx.omemo.OmemoConfiguration;
import org.jivesoftware.smackx.omemo.OmemoFingerprint;
import org.jivesoftware.smackx.omemo.OmemoManager;
import org.jivesoftware.smackx.omemo.OmemoService;
import org.jivesoftware.smackx.omemo.exceptions.CannotEstablishOmemoSessionException;
import org.jivesoftware.smackx.omemo.exceptions.UndecidedOmemoIdentityException;
import org.jivesoftware.smackx.omemo.internal.CipherAndAuthTag;
import org.jivesoftware.smackx.omemo.internal.OmemoDevice;
import org.jivesoftware.smackx.omemo.internal.OmemoMessageInformation;
import org.jivesoftware.smackx.omemo.listener.OmemoMessageListener;
import org.jivesoftware.smackx.omemo.signal.SignalOmemoService;
import org.jivesoftware.smackx.omemo.util.OmemoKeyUtil;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.whispersystems.libsignal.IdentityKey;

public class Messenger {

    private AbstractXMPPConnection connection;
    private OmemoManager omemoManager;
    private static Scanner scanner;

    public static void main(String[] args) throws Exception {
        String username = args[0];
        String password = args[1];
        Messenger messenger = new Messenger(username, password);

        scanner = new Scanner(System.in);
        while(true) {
            String input = scanner.nextLine();

            if (input.startsWith("/quit")) {
                break;
            }
            if (input.isEmpty()) {
                continue;
            }
            messenger.handleInput(input);
        }
    }

    public Messenger(String username, String password) throws Exception {
        connection = new XMPPTCPConnection(username, password);
        connection = connection.connect();
        connection.login();

        SignalOmemoService.acknowledgeLicense();
        SignalOmemoService.setup();
        OmemoConfiguration.setFileBasedOmemoStoreDefaultPath(new File("path"));
        omemoManager = OmemoManager.getInstanceFor(connection);
        omemoManager.addOmemoMessageListener(new OmemoMessageListener() {
            @Override
            public void onOmemoMessageReceived(String decryptedBody, Message encryptedMessage,
                                               Message wrappingMessage, OmemoMessageInformation omemoInformation) {
                IdentityKey senderKey = (IdentityKey) omemoInformation.getSenderIdentityKey().getIdentityKey();
                OmemoService<?,IdentityKey,?,?,?,?,?,?,?> service =
                        (OmemoService<?,IdentityKey,?,?,?,?,?,?,?>) OmemoService.getInstance();
                OmemoFingerprint fingerprint = service.getOmemoStoreBackend().keyUtil().getFingerprint(senderKey);
                boolean trusted = omemoManager.isTrustedOmemoIdentity(omemoInformation.getSenderDevice(),
                        fingerprint);
                System.out.println("(O) " + trusted + " " + encryptedMessage.getFrom() + ": " + decryptedBody);
            }

            @Override
            public void onOmemoKeyTransportReceived(CipherAndAuthTag cipherAndAuthTag, Message message, Message wrappingMessage, OmemoMessageInformation omemoInformation) {
            }
        });

        ChatManager.getInstanceFor(connection).addIncomingListener(
                (from, message, chat) -> System.out.println(from.asBareJid() + ": " + message)
        );
        omemoManager.initialize();
        System.out.println("Ready to chat");
    }

    private void handleInput(String input) throws Exception {
        String[] split = input.split(" ");
        String command = split[0];

        switch (command) {
            case "/say":
                if (split.length > 2) {
                    String recipient = split[1];
                    EntityBareJid recipientJid = JidCreate.entityBareFrom(recipient);

                    StringBuilder message = new StringBuilder(split[2]);
                    for (int i=3; i<split.length; i++) message.append(" ").append(split[i]);

                    ChatManager.getInstanceFor(connection).chatWith(recipientJid).send(message);
                }
                break;

            case "/omemo":
                if (split.length > 2) {
                    String recipient = split[1];
                    EntityBareJid recipientJid = JidCreate.entityBareFrom(recipient);

                    StringBuilder message = new StringBuilder();
                    for (int i=2; i<split.length; i++) message.append(split[i]);

                    Message encrypted = null;
                    try {
                        encrypted = OmemoManager.getInstanceFor(connection).encrypt(recipientJid, message.toString());
                        ChatManager.getInstanceFor(connection).chatWith(recipientJid).send(encrypted);
                    }
                    // Untrusted devices
                    catch (UndecidedOmemoIdentityException e) {
                        System.out.println("Untrusted Identities: ");
                        for (OmemoDevice device : e.getUntrustedDevices()) {
                            System.out.println(device);
                        }
                    }
                    catch (CannotEstablishOmemoSessionException e) {
                        encrypted = omemoManager.encryptForExistingSessions(e, message.toString());
                    }

                    if (encrypted != null) {
                        ChatManager.getInstanceFor(connection).chatWith(recipientJid).send(encrypted);
                    }
                }
                break;

            case "/trust":
                if (split.length == 2) {
                    BareJid contact = JidCreate.bareFrom(split[1]);
                    HashMap<OmemoDevice, OmemoFingerprint> fingerprints =
                            omemoManager.getActiveFingerprints(contact);

                    //Let user decide
                    for (OmemoDevice d : fingerprints.keySet()) {
                        System.out.println("Trust (1), or distrust (2)?");
                        System.out.println(OmemoKeyUtil.prettyFingerprint(fingerprints.get(d)));
                        int decision = Integer.parseInt(scanner.nextLine());
                        if (decision == 1) {
                            omemoManager.trustOmemoIdentity(d, fingerprints.get(d));
                        } else {
                            omemoManager.distrustOmemoIdentity(d, fingerprints.get(d));
                        }
                    }
                }
                break;
        }
    }
}
