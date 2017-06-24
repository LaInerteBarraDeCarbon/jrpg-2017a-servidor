package servidor;

import com.google.gson.Gson;

import estados.Estado;
import mensajeria.Comando;
import mensajeria.PaqueteDePersonajes;

/**
 * Clase que controla la atención a las conexiones.
 */
public class AtencionConexiones extends Thread {
	/**
	 * Gson. <br>
	 */
	private final Gson gson = new Gson();

	/**
	 * Crea la atención a conexiones. <br>
	 */
	public AtencionConexiones() {

	}

	/**
	 * Corre la atención de conexiones. <br>
	 */
	public void run() {
		synchronized (this) {
			try {
				while (true) { // Espero a que se conecte alguien
					wait();// Le reenvio la conexion a todos
					for (EscuchaCliente conectado : Servidor.getClientesConectados()) {
						if (conectado.getPaquetePersonaje().getEstado() != Estado.ESTADOOFFLINE) {
							PaqueteDePersonajes pdp = (PaqueteDePersonajes) new PaqueteDePersonajes(
									Servidor.getPersonajesConectados()).clone();
							pdp.setComando(Comando.CONEXION);
							conectado.getSalida().writeObject(gson.toJson(pdp));
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}