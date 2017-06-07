package testsServidor;

import java.io.IOException;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

import mensajeria.PaquetePersonaje;
import mensajeria.PaqueteUsuario;
import servidor.Conector;
import servidor.Servidor;

public class TestConector {

	/**
	 * Genera un nombre al azar. <br>
	 * No esperemos coherenecia.<br>
	 * 
	 * @return Nombre. <br>
	 */
	private String nombreRandom() {
		String abc = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
		StringBuilder nombre = new StringBuilder();
		Random random = new Random();
		while (nombre.length() < 8) {
			int posicion = (int) (random.nextFloat() * abc.length());
			nombre.append(abc.charAt(posicion));
		}
		String string = nombre.toString();
		return string;

	}

	@Test
	public void testConexionConLaDB() {
		new Servidor();
		Servidor.main(null);

		Conector conector = new Conector();
		conector.connect();

		// Pasado este punto la conexión con la base de datos result� exitosa

		Assert.assertEquals(1, 1);
		conector.close();
	}

	@Test
	public void testRegistrarUsuario() {
		new Servidor();
		Servidor.main(null);

		Conector conector = new Conector();
		conector.connect();

		PaqueteUsuario pu = new PaqueteUsuario();
		pu.setUsername("UserTest");
		pu.setPassword("test");

		conector.registrarUsuario(pu);

		pu = conector.getUsuario("UserTest");

		Assert.assertEquals("UserTest", pu.getUsername());
		conector.close();
	}

	@Test
	public void testRegistrarPersonaje() throws IOException {
		new Servidor();
		Servidor.main(null);

		Conector conector = new Conector();
		conector.connect();

		PaquetePersonaje pp = new PaquetePersonaje();
		pp.setCasta("Humano");
		pp.setDestreza(1);
		pp.setEnergiaTope(1);
		pp.setExperiencia(1);
		pp.setFuerza(1);
		pp.setInteligencia(1);
		pp.setNivel(1);
		pp.setNombre("PjTest");
		pp.setRaza("Asesino");
		pp.setSaludTope(1);

		PaqueteUsuario pu = new PaqueteUsuario();
		pu.setUsername(nombreRandom());
		pu.setPassword("test");

		conector.registrarUsuario(pu);
		conector.registrarPersonaje(pp, pu);

		pp = conector.getPersonaje(pu);

		Assert.assertEquals("PjTest", pp.getNombre());
	}

	@Test
	public void testLoginUsuario() {
		new Servidor();
		Servidor.main(null);

		Conector conector = new Conector();
		conector.connect();

		PaqueteUsuario pu = new PaqueteUsuario();
		pu.setUsername("UserTest");
		pu.setPassword("test");

		conector.registrarUsuario(pu);

		boolean resultadoLogin = conector.loguearUsuario(pu);

		Assert.assertEquals(true, resultadoLogin);
		conector.close();
	}

	@Test
	public void testLoginUsuarioFallido() {
		new Servidor();
		Servidor.main(null);

		Conector conector = new Conector();
		conector.connect();

		PaqueteUsuario pu = new PaqueteUsuario();
		pu.setUsername("userInventado");
		pu.setPassword("test");

		boolean resultadoLogin = conector.loguearUsuario(pu);

		Assert.assertEquals(false, resultadoLogin);
		conector.close();
	}

}
