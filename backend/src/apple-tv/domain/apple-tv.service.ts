import { Injectable } from '@nestjs/common';
import { exec } from 'child_process';
import { promisify } from 'util';

const execAsync = promisify(exec);

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

  async up() {
    // Logic to send "up" command to Apple TV
  }

  async down() {
    // Logic to send "down" command to Apple TV
  }

  async left() {
    // Logic to send "left" command to Apple TV
  }

  async right() {
    // Logic to send "right" command to Apple TV
  }

  async select() {
    // Logic to send "select" command to Apple TV
  }

  async menu() {
    // Logic to send "menu" command to Apple TV
  }
}
