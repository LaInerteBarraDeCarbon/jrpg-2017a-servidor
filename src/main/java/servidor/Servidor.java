package servidor;

import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import mensajeria.PaqueteMovimiento;
import mensajeria.PaquetePersonaje;

/**
 * Clase que administra el servidor. <br>
 */
public class Servidor extends Thread {
	/**
	 * Clientes conectados. <br>
	 */
	private static ArrayList<EscuchaCliente> clientesConectados = new ArrayList<>();
	/**
	 * Ubicación de los personajes. <br>
	 */
	private static Map<Integer, PaqueteMovimiento> ubicacionPersonajes = new HashMap<>();
	/**
	 * Personajes conectados. <br>
	 */
	private static Map<Integer, PaquetePersonaje> personajesConectados = new HashMap<>();
	/**
	 * Servidor. <br>
	 */
	private static Thread server;
	/**
	 * Socket del servidor. <br>
	 */
	private static ServerSocket serverSocket;
	/**
	 * Conector a la base de datos. <br>
	 */
	private static Conector conexionDB;
	/**
	 * Puerto. <br>
	 */
	private final int PUERTO = 9999;
	/**
	 * Ancho de la ventana. <br>
	 */
	private final static int ANCHO = 700;
	/**
	 * Alto de la ventana. <br>
	 */
	private final static int ALTO = 640;
	/**
	 * Alto del log. <br>
	 */
	private final static int ALTO_LOG = 520;
	/**
	 * Ancho del log. <br>
	 */
	private final static int ANCHO_LOG = ANCHO - 25;
	/**
	 * Campo del log. <br>
	 */
	public static JTextArea log;
	/**
	 * Atención conexiones. <br>
	 */
	public static AtencionConexiones atencionConexiones = new AtencionConexiones();
	/**
	 * Atención movimientos. <br>
	 */
	public static AtencionMovimientos atencionMovimientos = new AtencionMovimientos();

	/**
	 * Corre el servidor. <br>
	 * 
	 * @param args
	 *            Argumentos. <br>
	 */
	public static void main(String[] args) {
		cargarInterfaz();
	}

	/**
	 * Carga la interfaz de servidor. <br>
	 */
	private static void cargarInterfaz() {
		JFrame ventana = new JFrame("Servidor WOME");
		ventana.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		ventana.setSize(ANCHO, ALTO);
		ventana.setResizable(false);
		ventana.setLocationRelativeTo(null);
		ventana.setLayout(null);
		JLabel titulo = new JLabel("Log del servidor...");
		titulo.setFont(new Font("Courier New", Font.BOLD, 16));
		titulo.setBounds(10, 0, 200, 30);
		ventana.add(titulo);
		log = new JTextArea();
		log.setEditable(false);
		log.setFont(new Font("Times New Roman", Font.PLAIN, 13));
		JScrollPane scroll = new JScrollPane(log, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scroll.setBounds(10, 40, ANCHO_LOG, ALTO_LOG);
		ventana.add(scroll);
		final JButton botonIniciar = new JButton();
		final JButton botonDetener = new JButton();
		botonIniciar.setText("Iniciar");
		botonIniciar.setBounds(220, ALTO - 70, 100, 30);
		botonIniciar.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				server = new Thread(new Servidor());
				server.start();
				botonIniciar.setEnabled(false);
				botonDetener.setEnabled(true);
			}
		});
		ventana.add(botonIniciar);
		botonDetener.setText("Detener");
		botonDetener.setBounds(360, ALTO - 70, 100, 30);
		botonDetener.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				try {
					server.stop();
					for (EscuchaCliente cliente : clientesConectados) {
						cliente.getSalida().close();
						cliente.getEntrada().close();
						cliente.getSocket().close();
					}
					serverSocket.close();
				} catch (IOException e1) {
					log.append("Fallo al intentar detener el servidor." + System.lineSeparator());
				}
				if (conexionDB != null) {
					conexionDB.close();
				}
				botonDetener.setEnabled(false);
				botonIniciar.setEnabled(true);
			}
		});
		botonDetener.setEnabled(false);
		ventana.add(botonDetener);
		ventana.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		ventana.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent evt) {
				if (serverSocket != null) {
					try {
						server.stop();
						for (EscuchaCliente cliente : clientesConectados) {
							cliente.getSalida().close();
							cliente.getEntrada().close();
							cliente.getSocket().close();
						}
						serverSocket.close();
					} catch (IOException e) {
						log.append("Fallo al intentar detener el servidor." + System.lineSeparator());
						System.exit(1);
					}
				}
				if (conexionDB != null) {
					conexionDB.close();
				}
				System.exit(0);
			}
		});
		ventana.setVisible(true);
	}

	/**
	 * Corre el servidor. <br>
	 */
	public void run() {
		try {
			conexionDB = new Conector();
			conexionDB.connect();
			log.append("Iniciando el servidor..." + System.lineSeparator());
			serverSocket = new ServerSocket(PUERTO);
			log.append("Servidor esperando conexiones..." + System.lineSeparator());
			String ipRemota;
			atencionConexiones.start();
			atencionMovimientos.start();
			while (true) {
				Socket cliente = serverSocket.accept();
				ipRemota = cliente.getInetAddress().getHostAddress();
				log.append(ipRemota + " se ha conectado" + System.lineSeparator());
				ObjectOutputStream salida = new ObjectOutputStream(cliente.getOutputStream());
				ObjectInputStream entrada = new ObjectInputStream(cliente.getInputStream());
				EscuchaCliente atencion = new EscuchaCliente(ipRemota, cliente, entrada, salida);
				atencion.start();
				clientesConectados.add(atencion);
			}
		} catch (Exception e) {
			log.append("Fallo la conexión." + System.lineSeparator());
		}
	}

	/**
	 * Devuelve los clientes conectados. <br>
	 * 
	 * @return Clientes conectados. <br>
	 */
	public static ArrayList<EscuchaCliente> getClientesConectados() {
		return clientesConectados;
	}

	/**
	 * Devuelve la ubicación de los personajes. <br>
	 * 
	 * @return Ubicación de los personajes. <br>
	 */
	public static Map<Integer, PaqueteMovimiento> getUbicacionPersonajes() {
		return ubicacionPersonajes;
	}

	/**
	 * Devuelve los personajes conectados. <br>
	 * 
	 * @return Personajes conectados. <br>
	 */
	public static Map<Integer, PaquetePersonaje> getPersonajesConectados() {
		return personajesConectados;
	}

	/**
	 * Devuelve el conector con la base de datos. <br>
	 * 
	 * @return Conector de la base de datos. <br>
	 */
	public static Conector getConector() {
		return conexionDB;
	}
}