package servidor;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

import com.google.gson.Gson;

import estados.Estado;
import mensajeria.Comando;
import mensajeria.Paquete;
import mensajeria.PaqueteAtacar;
import mensajeria.PaqueteBatalla;
import mensajeria.PaqueteDeMovimientos;
import mensajeria.PaqueteDePersonajes;
import mensajeria.PaqueteFinalizarBatalla;
import mensajeria.PaqueteMovimiento;
import mensajeria.PaquetePersonaje;
import mensajeria.PaqueteUsuario;

/**
 * Clase que "escucha" las acciones del usuario. <br>
 */
public class EscuchaCliente extends Thread {
	/**
	 * Socket del cliente. <br>
	 */
	private final Socket socket;
	/**
	 * Entrada del cliente. <br>
	 */
	private final ObjectInputStream entrada;
	/**
	 * Salida del cliente. <br>
	 */
	private final ObjectOutputStream salida;
	/**
	 * ID del personaje. <br>
	 */
	private int idPersonaje;
	/**
	 * GSon. <br>
	 */
	private final Gson gson = new Gson();
	/**
	 * Personaje del cliente. <br>
	 */
	private PaquetePersonaje paquetePersonaje;
	/**
	 * Movimientos del personaje. <br>
	 */
	private PaqueteMovimiento paqueteMovimiento;
	/**
	 * Acciones de batalla del personaje. <br>
	 */
	private PaqueteBatalla paqueteBatalla;
	/**
	 * Ataques del personaje. <br>
	 */
	private PaqueteAtacar paqueteAtacar;
	/**
	 * Acciones post batalla del personaje. <br>
	 */
	private PaqueteFinalizarBatalla paqueteFinalizarBatalla;
	/**
	 * Opciones de movimiento. <br>
	 */
	private PaqueteDeMovimientos paqueteDeMovimiento;
	/**
	 * Opciones de personajes .<br>
	 */
	private PaqueteDePersonajes paqueteDePersonajes;

	/**
	 * Crea el escucha de cliente. <br>
	 * 
	 * @param ip
	 *            IP del cliente. <br>
	 * @param socket
	 *            Socket del cliente. <br>
	 * @param entrada
	 *            Entrada. <br>
	 * @param salida
	 *            Salida. <br>
	 * @throws IOException
	 *             Error al abrir. <br>
	 */
	public EscuchaCliente(String ip, Socket socket, ObjectInputStream entrada, ObjectOutputStream salida)
			throws IOException {
		this.socket = socket;
		this.entrada = entrada;
		this.salida = salida;
		paquetePersonaje = new PaquetePersonaje();
	}

	/**
	 * Corre el escucha. <br>
	 */
	public void run() {
		try {
			Paquete paquete;
			Paquete paqueteSv = new Paquete(null, 0);
			PaqueteUsuario paqueteUsuario = new PaqueteUsuario();
			String cadenaLeida = (String) entrada.readObject();
			while (!((paquete = gson.fromJson(cadenaLeida, Paquete.class)).getComando() == Comando.DESCONECTAR)) {
				switch (paquete.getComando()) {
				case Comando.REGISTRO:
					// Paquete que le voy a enviar al usuario
					paqueteSv.setComando(Comando.REGISTRO);
					paqueteUsuario = (PaqueteUsuario) (gson.fromJson(cadenaLeida, PaqueteUsuario.class)).clone();
					// Si el usuario se pudo registrar le envio un msj de exito
					if (Servidor.getConector().registrarUsuario(paqueteUsuario)) {
						paqueteSv.setMensaje(Paquete.MSJEXITO);
						salida.writeObject(gson.toJson(paqueteSv));
						// Si el usuario no se pudo registrar le envio un msj de
						// fracaso
					} else {
						paqueteSv.setMensaje(Paquete.MSJFRACASO);
						salida.writeObject(gson.toJson(paqueteSv));
					}
					break;
				case Comando.CREACIONPJ:
					// Casteo el paquete personaje
					paquetePersonaje = (PaquetePersonaje) (gson.fromJson(cadenaLeida, PaquetePersonaje.class));
					// Guardo el personaje en ese usuario
					Servidor.getConector().registrarPersonaje(paquetePersonaje, paqueteUsuario);
					// Le envio el id del personaje
					salida.writeObject(gson.toJson(paquetePersonaje, paquetePersonaje.getClass()));
					break;
				case Comando.INICIOSESION:
					paqueteSv.setComando(Comando.INICIOSESION);
					// Recibo el paquete usuario
					paqueteUsuario = (PaqueteUsuario) (gson.fromJson(cadenaLeida, PaqueteUsuario.class));
					// Si se puede loguear el usuario le envio un mensaje de
					// exito y el paquete personaje con los datos
					if (Servidor.getConector().loguearUsuario(paqueteUsuario)) {
						paquetePersonaje = new PaquetePersonaje();
						paquetePersonaje = Servidor.getConector().getPersonaje(paqueteUsuario);
						paquetePersonaje.setComando(Comando.INICIOSESION);
						paquetePersonaje.setMensaje(Paquete.MSJEXITO);
						idPersonaje = paquetePersonaje.getId();
						salida.writeObject(gson.toJson(paquetePersonaje));
					} else {
						paqueteSv.setMensaje(Paquete.MSJFRACASO);
						salida.writeObject(gson.toJson(paqueteSv));
					}
					break;
				case Comando.SALIR:
					entrada.close();
					salida.close();
					socket.close();
					// Lo elimino de los clientes conectados
					Servidor.getClientesConectados().remove(this);
					// Indico que se desconecto
					Servidor.log.append(paquete.getIp() + " se ha desconectado." + System.lineSeparator());
					return;
				case Comando.CONEXION:
					paquetePersonaje = (PaquetePersonaje) (gson.fromJson(cadenaLeida, PaquetePersonaje.class)).clone();
					Servidor.getPersonajesConectados().put(paquetePersonaje.getId(),
							(PaquetePersonaje) paquetePersonaje.clone());
					Servidor.getUbicacionPersonajes().put(paquetePersonaje.getId(),
							(PaqueteMovimiento) new PaqueteMovimiento(paquetePersonaje.getId()).clone());
					synchronized (Servidor.atencionConexiones) {
						Servidor.atencionConexiones.notify();
					}
					break;
				case Comando.MOVIMIENTO:
					paqueteMovimiento = (PaqueteMovimiento) (gson.fromJson((String) cadenaLeida,
							PaqueteMovimiento.class));
					Servidor.getUbicacionPersonajes().get(paqueteMovimiento.getIdPersonaje())
							.setPosX(paqueteMovimiento.getPosX());
					Servidor.getUbicacionPersonajes().get(paqueteMovimiento.getIdPersonaje())
							.setPosY(paqueteMovimiento.getPosY());
					Servidor.getUbicacionPersonajes().get(paqueteMovimiento.getIdPersonaje())
							.setDireccion(paqueteMovimiento.getDireccion());
					Servidor.getUbicacionPersonajes().get(paqueteMovimiento.getIdPersonaje())
							.setFrame(paqueteMovimiento.getFrame());
					synchronized (Servidor.atencionMovimientos) {
						Servidor.atencionMovimientos.notify();
					}
					break;
				case Comando.MOSTRARMAPAS:
					// Indico en el log que el usuario se conecto a ese mapa
					paquetePersonaje = (PaquetePersonaje) gson.fromJson(cadenaLeida, PaquetePersonaje.class);
					Servidor.log.append(socket.getInetAddress().getHostAddress() + " ha elegido el mapa "
							+ paquetePersonaje.getMapa() + System.lineSeparator());
					break;
				case Comando.BATALLA:
					// Le reenvio al id del personaje batallado que quieren
					// pelear
					paqueteBatalla = (PaqueteBatalla) gson.fromJson(cadenaLeida, PaqueteBatalla.class);
					Servidor.log.append(paqueteBatalla.getId() + " quiere batallar con " + paqueteBatalla.getIdEnemigo()
							+ System.lineSeparator());
					// seteo estado de batalla
					Servidor.getPersonajesConectados().get(paqueteBatalla.getId()).setEstado(Estado.ESTADOBATALLA);
					Servidor.getPersonajesConectados().get(paqueteBatalla.getIdEnemigo())
							.setEstado(Estado.ESTADOBATALLA);
					paqueteBatalla.setMiTurno(true);
					salida.writeObject(gson.toJson(paqueteBatalla));
					for (EscuchaCliente conectado : Servidor.getClientesConectados()) {
						if (conectado.getIdPersonaje() == paqueteBatalla.getIdEnemigo()) {
							int aux = paqueteBatalla.getId();
							paqueteBatalla.setId(paqueteBatalla.getIdEnemigo());
							paqueteBatalla.setIdEnemigo(aux);
							paqueteBatalla.setMiTurno(false);
							conectado.getSalida().writeObject(gson.toJson(paqueteBatalla));
							break;
						}
					}
					synchronized (Servidor.atencionConexiones) {
						Servidor.atencionConexiones.notify();
					}
					break;
				case Comando.ATACAR:
					paqueteAtacar = (PaqueteAtacar) gson.fromJson(cadenaLeida, PaqueteAtacar.class);
					for (EscuchaCliente conectado : Servidor.getClientesConectados()) {
						if (conectado.getIdPersonaje() == paqueteAtacar.getIdEnemigo()) {
							conectado.getSalida().writeObject(gson.toJson(paqueteAtacar));
						}
					}
					break;
				case Comando.FINALIZARBATALLA:
					paqueteFinalizarBatalla = (PaqueteFinalizarBatalla) gson.fromJson(cadenaLeida,
							PaqueteFinalizarBatalla.class);
					Servidor.getPersonajesConectados().get(paqueteFinalizarBatalla.getId())
							.setEstado(Estado.ESTADOJUEGO);
					Servidor.getPersonajesConectados().get(paqueteFinalizarBatalla.getIdEnemigo())
							.setEstado(Estado.ESTADOJUEGO);
					for (EscuchaCliente conectado : Servidor.getClientesConectados()) {
						if (conectado.getIdPersonaje() == paqueteFinalizarBatalla.getIdEnemigo()) {
							conectado.getSalida().writeObject(gson.toJson(paqueteFinalizarBatalla));
						}
					}
					synchronized (Servidor.atencionConexiones) {
						Servidor.atencionConexiones.notify();
					}
					break;
				case Comando.ACTUALIZARPERSONAJE:
					paquetePersonaje = (PaquetePersonaje) gson.fromJson(cadenaLeida, PaquetePersonaje.class);
					Servidor.getConector().actualizarPersonaje(paquetePersonaje);
					Servidor.getPersonajesConectados().remove(paquetePersonaje.getId());
					Servidor.getPersonajesConectados().put(paquetePersonaje.getId(), paquetePersonaje);
					for (EscuchaCliente conectado : Servidor.getClientesConectados()) {
						conectado.getSalida().writeObject(gson.toJson(paquetePersonaje));
					}
					break;
				case Comando.ACTUALIZARINVENTARIO:
					paquetePersonaje = (PaquetePersonaje) gson.fromJson(cadenaLeida, PaquetePersonaje.class);
					Servidor.getConector().actualizarInventario(paquetePersonaje);
					Servidor.getPersonajesConectados().remove(paquetePersonaje.getId());
					Servidor.getPersonajesConectados().put(paquetePersonaje.getId(), paquetePersonaje);
					paquetePersonaje.obtenerBonus();
					for (EscuchaCliente conectado : Servidor.getClientesConectados()) {
						conectado.getSalida().writeObject(gson.toJson(paquetePersonaje));
					}
					break;
				default:
					break;
				}
				cadenaLeida = (String) entrada.readObject();
			}
			entrada.close();
			salida.close();
			socket.close();
			Servidor.getPersonajesConectados().remove(paquetePersonaje.getId());
			Servidor.getUbicacionPersonajes().remove(paquetePersonaje.getId());
			Servidor.getClientesConectados().remove(this);
			for (EscuchaCliente conectado : Servidor.getClientesConectados()) {
				paqueteDePersonajes = new PaqueteDePersonajes(Servidor.getPersonajesConectados());
				paqueteDePersonajes.setComando(Comando.CONEXION);
				conectado.salida.writeObject(gson.toJson(paqueteDePersonajes, PaqueteDePersonajes.class));
			}
			Servidor.log.append(paquete.getIp() + " se ha desconectado." + System.lineSeparator());
		} catch (IOException | ClassNotFoundException e) {
			Servidor.log.append("Error de conexion: " + e.getMessage() + System.lineSeparator());
		}
	}

	/**
	 * Devuelve el socket. <br>
	 * 
	 * @return Socket. <br>
	 */
	public Socket getSocket() {
		return socket;
	}

	/**
	 * Devuelve la entrada. <br>
	 * 
	 * @return Entrada. <br>
	 */
	public ObjectInputStream getEntrada() {
		return entrada;
	}

	/**
	 * Devuelve la salida. <br>
	 * 
	 * @return Salida. <br>
	 */
	public ObjectOutputStream getSalida() {
		return salida;
	}

	/**
	 * Devuelve al personaje. <br>
	 * 
	 * @return Personaje. <br>
	 */
	public PaquetePersonaje getPaquetePersonaje() {
		return paquetePersonaje;
	}

	/**
	 * Devuelve el ID del personaje. <br>
	 * 
	 * @return ID personaje. <br>
	 */
	public int getIdPersonaje() {
		return idPersonaje;
	}
}
