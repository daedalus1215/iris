import { Controller, Post, Get, Body } from '@nestjs/common';
import { AppleTvService } from '../domain/apple-tv.service';

@Controller('apple-tv')
export class AppleTvController {
  constructor(private readonly appleTvService: AppleTvService) {}

  async execute(mac: string, protocol: string) {
    return this.appleTvService.connect(mac, protocol);
  }

  @Post('connect')
  async connect(@Body('mac') mac: string, @Body('protocol') protocol: string) {
    return this.appleTvService.connect(mac, protocol);
  }

  @Post('complete-pairing')
  async completePairing(
    @Body('pairingId') pairingId: string,
    @Body('code') code: string,
  ) {
    return this.appleTvService.completePairing(pairingId, code);
  }

  @Post('up')
  async up(@Body('mac') mac: string) {
    return this.appleTvService.up(mac);
  }

  @Post('down')
  async down(@Body('mac') mac: string) {
    return this.appleTvService.down(mac);
  }

  @Post('left')
  async left(@Body('mac') mac: string) {
    return this.appleTvService.left(mac);
  }

  @Post('right')
  async right(@Body('mac') mac: string) {
    return this.appleTvService.right(mac);
  }

  @Post('select')
  async select(@Body('mac') mac: string) {
    return this.appleTvService.select(mac);
  }

  @Post('menu')
  async menu(@Body('mac') mac: string) {
    return this.appleTvService.menu(mac);
  }

  @Get('scan')
  async scan() {
    return this.appleTvService.scan();
  }
}
