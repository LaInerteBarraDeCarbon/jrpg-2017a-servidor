package servidor;

import com.google.gson.Gson;

import estados.Estado;
import mensajeria.Comando;
import mensajeria.PaqueteDeMovimientos;

/**
 * Clase que esta atenta a los movimientos de los personajes. Si algún personaje
 * se mueve, transmite la información a todos los clientes. <br>
 */
public class AtencionMovimientos extends Thread {
	/**
	 * Gson. <br>
	 */
	private final Gson gson = new Gson();

	/**
	 * Crea la atención a movimientos. <br>
	 */
	public AtencionMovimientos() {

	}

	/**
	 * Corre la atención a movimientos. <br>
	 */
	public void run() {
		synchronized (this) {
			try {
				while (true) {// Espero a que se conecte alguien
					wait();// Le reenvio la conexion a todos
					for (EscuchaCliente conectado : Servidor.getClientesConectados()) {
						if (conectado.getPaquetePersonaje().getEstado() == Estado.ESTADOJUEGO) {
							PaqueteDeMovimientos pdp = (PaqueteDeMovimientos) new PaqueteDeMovimientos(
									Servidor.getUbicacionPersonajes()).clone();
							pdp.setComando(Comando.MOVIMIENTO);
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
