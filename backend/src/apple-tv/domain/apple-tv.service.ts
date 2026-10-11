import { Injectable, NotAcceptableException } from '@nestjs/common';
import { exec } from 'child_process';
import { promisify } from 'util';
import { spawn } from 'child_process';

const execAsync = promisify(exec);

// Temporary in-memory store for pairing state
const pairingState = new Map<string, { mac: string; protocol: string }>();

@Injectable()
export class AppleTvService {
  async scan(): Promise<any[]> {
    try {
      const { stdout } = await execAsync('atvremote scan');
      return this.parseScanOutput(stdout);
    } catch (error) {
      throw new Error(`Scan failed: ${error.message}`);
    }
  }

  async connect(mac: string, protocol: string) {
    try {
      // Scan for devices
      const { stdout } = await execAsync(`atvremote scan`);
      if (!stdout.includes(mac)) {
        throw new Error(`Device with MAC ${mac} not found.`);
      }

      // Generate a temporary pairing ID to track this session
      const pairingId = `${mac}-${protocol}-${Date.now()}`;
      pairingState.set(pairingId, { mac, protocol });

      // Start pairing as a detached process
      const pairProcess = spawn(
        'atvremote',
        ['--id', mac, '--protocol', protocol, 'pair'],
        {
          detached: true,
          stdio: 'ignore', // Ignore output since we're not handling it here
        }
      );

      pairProcess.unref(); // Allow the process to continue in the background

      console.log('pairingId', pairingId);
      return {
        message: 'Code Inc',
        pairingId, // Return pairing ID to be used in the next request
      };
    } catch (error) {
      throw new Error(`Failed to connect: ${error.message}`);
    }
  }

  async completePairing(pairingId: string, code: string) {
    const state = pairingState.get(pairingId);
    console.log('state', state);
    if (!state) {
      throw new NotAcceptableException(
        'Invalid pairing ID or pairing has expired.',
      );
    }

    const { mac, protocol } = state;
    try {
      const pairResult = await execAsync(
        `atvremote --id ${mac} --protocol ${protocol} pair --pin ${code}`,
      );

      if (pairResult.stdout.includes('Pairing successful')) {
        const newCredentials = await this.getCredentials(mac, protocol);
        pairingState.delete(pairingId); 
        return { message: 'Pairing successful', credentials: newCredentials };
      }
      throw new NotAcceptableException('Pairing failed. Incorrect code?');
    } catch (error: unknown) {
      console.log(
        `Failed to complete pairing: ${error instanceof Error ? error.message : String(error)}`,
      );
      throw new NotAcceptableException(
        `Failed to complete pairing: ${error instanceof Error ? error.message : String(error)}`,
      );
    }
  }

  private parseScanOutput(output: string): any[] {
    const devices: { name: string; address: string; mac: string }[] = [];
    const deviceBlocks = output.split('\n\n');

    for (const block of deviceBlocks) {
      const lines = block.split('\n').map((line) => line.trim());

      const name = lines
        .find((line) => line.startsWith('Name:'))
        ?.split('Name: ')[1];
      const address = lines
        .find((line) => line.startsWith('Address:'))
        ?.split('Address: ')[1];
      const mac = lines
        .find((line) => line.startsWith('MAC:'))
        ?.split('MAC: ')[1];

      if (name && address && mac) {
        devices.push({ name, address, mac });
      }
    }

    return devices;
  }

  private async getCredentials(mac: string, protocol: string) {
    try {
      const { stdout } = await execAsync(
        `atvremote --id ${mac} --protocol ${protocol} credentials`,
      );
      return stdout.trim() || null;
    } catch {
      return null;
    }
  }

  async up(mac: string) {
    return this.executeCommand(mac, 'up');
  }

  async down(mac: string) {
    return this.executeCommand(mac, 'down');
  }

  async left(mac: string) {
    return this.executeCommand(mac, 'left');
  }

  async right(mac: string) {
    return this.executeCommand(mac, 'right');
  }

  async select(mac: string) {
    return this.executeCommand(mac, 'select');
  }

  async menu(mac: string) {
    try {
      return this.executeCommand(mac, 'menu');
    } catch (error) {
      throw new NotAcceptableException(
        `Failed to execute command: ${error instanceof Error ? error.message : String(error)}`,
      );
    }
}

  async home(mac: string) {
    return this.executeCommand(mac, 'home');
  }

  async play(mac: string) {
    return this.executeCommand(mac, 'play');
  }

  async pause(mac: string) {
    return this.executeCommand(mac, 'pause');
  }

  async next(mac: string) {
    return this.executeCommand(mac, 'next');
  }

  async previous(mac: string) {
    return this.executeCommand(mac, 'previous');
  }

  async powerOff(mac: string) {
    return this.executeCommand(mac, 'turn_off');
  }

  async powerOn(mac: string) {
    return this.executeCommand(mac, 'turn_on');
  }

  private async executeCommand(mac: string, command: string) {
    return execAsync(`atvremote --id ${mac} ${command}`);
  }
}
